import { http, HttpResponse, type JsonBodyType } from 'msw';
import type { z } from 'zod';
import {
  ApiErrorBodySchema,
  AttemptListSchema,
  CreateAppResponseSchema,
  CreateEndpointResponseSchema,
  DELIVERY_STATUSES,
  DeliveryDetailSchema,
  DeliveryPageSchema,
  EndpointListSchema,
  EndpointResponseSchema,
  EVENT_TYPE_FILTER_PATTERN,
  EVENT_TYPE_PATTERN,
  EventResponseSchema,
  MAX_DELIVERY_PAGE,
  MAX_PAYLOAD_BYTES,
  PublishEventResponseSchema,
  ReplayResponseSchema,
  type DeliveryStatus,
  type FieldError,
} from '@/api/schemas';
import {
  compareNewestFirst,
  db,
  decodeCursor,
  encodeCursor,
  filterMatches,
  isOlderThanCursor,
  nextId,
  nowIso,
  type StoredEndpoint,
} from './db';

/*
 * MSW handlers mirroring relay-server's controllers. Every body goes through the real zod
 * schema before it is sent, so a mock that drifts from the contract throws in tests.
 */

export const MOCK_ADMIN_TOKEN = 'dev-admin-token';
/** Any `rl_live_` key is accepted, except ones containing "revoked" (to exercise 401). */
const isValidKey = (k: string) => k.startsWith('rl_live_') && !k.includes('revoked');

const API = '*/api/v1';

function send<T>(schema: z.ZodType<T>, data: T, status = 200, headers?: Record<string, string>) {
  return HttpResponse.json(schema.parse(data) as JsonBodyType, { status, headers });
}

const REASONS: Record<number, string> = {
  400: 'Bad Request',
  401: 'Unauthorized',
  404: 'Not Found',
  409: 'Conflict',
  413: 'Content Too Large',
};

export function apiError(
  request: Request,
  status: number,
  code: string,
  message: string,
  fieldErrors: FieldError[] = [],
) {
  const body = {
    timestamp: new Date().toISOString(),
    status,
    error: REASONS[status] ?? 'Error',
    code,
    message,
    path: new URL(request.url).pathname,
    ...(fieldErrors.length > 0 ? { fieldErrors } : {}),
  };
  return HttpResponse.json(ApiErrorBodySchema.parse(body), { status });
}

const validation = (req: Request, field: string, message: string) =>
  apiError(req, 400, 'VALIDATION_ERROR', 'Validation failed', [{ field, message }]);

function bearer(request: Request): string | null {
  const h = request.headers.get('Authorization');
  if (!h || !/^bearer /i.test(h)) return null;
  const t = h.slice(7).trim();
  return t === '' ? null : t;
}

function requireKey(request: Request) {
  const t = bearer(request);
  return t !== null && isValidKey(t)
    ? null
    : apiError(request, 401, 'UNAUTHORIZED', 'Missing or invalid API key');
}

async function jsonBody(request: Request): Promise<Record<string, unknown> | null> {
  const text = await request.text();
  if (text === '') return {};
  try {
    const v: unknown = JSON.parse(text);
    return typeof v === 'object' && v !== null && !Array.isArray(v)
      ? (v as Record<string, unknown>)
      : null;
  } catch {
    return null;
  }
}

const malformed = (req: Request) => apiError(req, 400, 'MALFORMED_REQUEST', 'Malformed request');

function findEndpoint(id: string): StoredEndpoint | undefined {
  return db.current.endpoints.find((e) => e.id === id && e.status !== 'DISABLED');
}

const publicEndpoint = ({ ...e }: StoredEndpoint) => e;

/** SsrfGuard.validate, approximated without DNS: scheme, credentials, obvious internal hosts. */
function ssrfProblem(raw: string): string | null {
  let u: URL;
  try {
    u = new URL(raw.trim());
  } catch {
    return 'url is not a valid URL';
  }
  if (u.protocol !== 'http:' && u.protocol !== 'https:') return 'url must use http or https';
  if (u.username || u.password) return 'url must have a host and no credentials';
  const h = u.hostname.replace(/^\[|\]$/g, '');
  if (
    /^(localhost|127\.|10\.|192\.168\.|172\.(1[6-9]|2\d|3[01])\.|169\.254\.|0\.|::1$|fc|fd)/i.test(
      h,
    )
  ) {
    return 'url must not target a private or loopback address';
  }
  if (h.endsWith('.invalid')) return 'url host could not be resolved';
  return null;
}

function checkFilter(req: Request, filter: unknown) {
  if (filter === undefined || filter === null) return null;
  if (typeof filter !== 'string' || !EVENT_TYPE_FILTER_PATTERN.test(filter)) {
    return validation(req, 'eventTypeFilter', 'invalid event type filter');
  }
  return null;
}

const endpointHandlers = [
  http.post(`${API}/apps`, async ({ request }) => {
    if (bearer(request) !== MOCK_ADMIN_TOKEN)
      return apiError(request, 401, 'UNAUTHORIZED', 'Missing or invalid admin token');
    const body = await jsonBody(request);
    if (body === null) return malformed(request);
    const name = typeof body.name === 'string' ? body.name.trim() : '';
    if (name === '') return validation(request, 'name', 'name is required');
    const apiKey = `rl_live_${nextId(db.current).replace(/-/g, '')}mock`;
    return send(
      CreateAppResponseSchema,
      { id: nextId(db.current), name, apiKey, apiKeyPrefix: apiKey.slice(0, 12) },
      201,
    );
  }),

  http.get(`${API}/endpoints`, ({ request }) => {
    const denied = requireKey(request);
    if (denied) return denied;
    return send(
      EndpointListSchema,
      db.current.endpoints.filter((e) => e.status !== 'DISABLED').map(publicEndpoint),
    );
  }),

  http.post(`${API}/endpoints`, async ({ request }) => {
    const denied = requireKey(request);
    if (denied) return denied;
    const body = await jsonBody(request);
    if (body === null) return malformed(request);
    if (typeof body.url !== 'string' || body.url.trim() === '')
      return validation(request, 'url', 'url is required');
    const bad = checkFilter(request, body.eventTypeFilter);
    if (bad) return bad;
    const ssrf = ssrfProblem(body.url);
    if (ssrf) return validation(request, 'url', ssrf);
    const ts = nowIso(db.current);
    const filter =
      typeof body.eventTypeFilter === 'string' && body.eventTypeFilter.trim() !== ''
        ? body.eventTypeFilter
        : '*';
    const e: StoredEndpoint = {
      id: nextId(db.current),
      url: body.url.trim(),
      description: typeof body.description === 'string' ? body.description : null,
      eventTypeFilter: filter,
      status: 'ACTIVE',
      consecutiveFailures: 0,
      pausedReason: null,
      createdAt: ts,
      updatedAt: ts,
    };
    db.current.endpoints.push(e);
    const secret = `whsec_${btoa(e.id + e.id)
      .replace(/=+$/, '')
      .replace(/\+/g, '-')
      .replace(/\//g, '_')
      .slice(0, 43)}`;
    return send(CreateEndpointResponseSchema, { ...publicEndpoint(e), signingSecret: secret }, 201);
  }),

  http.get(`${API}/endpoints/:id`, ({ request, params }) => {
    const denied = requireKey(request);
    if (denied) return denied;
    const e = findEndpoint(String(params.id));
    return e
      ? send(EndpointResponseSchema, publicEndpoint(e))
      : apiError(request, 404, 'NOT_FOUND', 'Endpoint not found');
  }),

  http.patch(`${API}/endpoints/:id`, async ({ request, params }) => {
    const denied = requireKey(request);
    if (denied) return denied;
    const body = await jsonBody(request);
    if (body === null) return malformed(request);
    const bad = checkFilter(request, body.eventTypeFilter);
    if (bad) return bad;
    const e = findEndpoint(String(params.id));
    if (!e) return apiError(request, 404, 'NOT_FOUND', 'Endpoint not found');
    if (typeof body.url === 'string') {
      const ssrf = ssrfProblem(body.url);
      if (ssrf) return validation(request, 'url', ssrf);
      e.url = body.url.trim();
    }
    if (typeof body.description === 'string') e.description = body.description;
    if (typeof body.eventTypeFilter === 'string') e.eventTypeFilter = body.eventTypeFilter;
    e.updatedAt = nowIso(db.current);
    return send(EndpointResponseSchema, publicEndpoint(e));
  }),

  http.delete(`${API}/endpoints/:id`, ({ request, params }) => {
    const denied = requireKey(request);
    if (denied) return denied;
    const e = findEndpoint(String(params.id));
    if (!e) return apiError(request, 404, 'NOT_FOUND', 'Endpoint not found');
    e.status = 'DISABLED';
    return new HttpResponse(null, { status: 204 });
  }),

  http.post(`${API}/endpoints/:id/pause`, async ({ request, params }) => {
    const denied = requireKey(request);
    if (denied) return denied;
    const body = await jsonBody(request);
    if (body === null) return malformed(request);
    const reason = typeof body.reason === 'string' ? body.reason : null;
    if (reason !== null && reason.length > 500)
      return validation(request, 'reason', 'reason must be at most 500 characters');
    const e = findEndpoint(String(params.id));
    if (!e) return apiError(request, 404, 'NOT_FOUND', 'Endpoint not found');
    e.status = 'PAUSED';
    e.pausedReason = reason === null || reason.trim() === '' ? 'Paused manually' : reason;
    e.updatedAt = nowIso(db.current);
    return send(EndpointResponseSchema, publicEndpoint(e));
  }),

  http.post(`${API}/endpoints/:id/resume`, ({ request, params }) => {
    const denied = requireKey(request);
    if (denied) return denied;
    const e = findEndpoint(String(params.id));
    if (!e) return apiError(request, 404, 'NOT_FOUND', 'Endpoint not found');
    e.status = 'ACTIVE';
    e.pausedReason = null;
    e.consecutiveFailures = 0;
    e.updatedAt = nowIso(db.current);
    return send(EndpointResponseSchema, publicEndpoint(e));
  }),
];

function summaries(eventId: string) {
  return db.current.deliveries
    .filter((d) => d.eventId === eventId)
    .map((d) => ({
      id: d.id,
      endpointId: d.endpointId,
      status: d.status,
      attemptCount: d.attemptCount,
      nextAttemptAt: d.nextAttemptAt,
      lastStatusCode: d.lastStatusCode,
    }));
}

const eventHandlers = [
  http.post(`${API}/events`, async ({ request }) => {
    const denied = requireKey(request);
    if (denied) return denied;
    const body = await jsonBody(request);
    if (body === null) return malformed(request);
    if (typeof body.type !== 'string' || body.type.trim() === '')
      return validation(request, 'type', 'type is required');
    if (!('payload' in body)) return validation(request, 'payload', 'payload is required');
    if (!EVENT_TYPE_PATTERN.test(body.type))
      return validation(request, 'type', 'type must match ^[a-z0-9._-]{1,100}$');
    if (body.payload === null) return validation(request, 'payload', 'payload is required');
    const json = JSON.stringify(body.payload);
    if (new TextEncoder().encode(json).length > MAX_PAYLOAD_BYTES) {
      return apiError(request, 413, 'PAYLOAD_TOO_LARGE', 'payload must be at most 256 KB');
    }
    const keyHeader = request.headers.get('Idempotency-Key');
    const key = keyHeader && keyHeader.trim() !== '' ? keyHeader : null;
    if (key) {
      const existing = db.current.events.find((e) => e.idempotencyKey === key);
      if (existing) {
        // Like EventService.replay: compares the payload hash only, not the type.
        if (existing.payloadJson !== json) {
          return apiError(
            request,
            409,
            'CONFLICT',
            'Idempotency key was already used with a different payload',
          );
        }
        const count = db.current.deliveries.filter((d) => d.eventId === existing.id).length;
        return send(
          PublishEventResponseSchema,
          {
            id: existing.id,
            type: existing.type,
            createdAt: existing.createdAt,
            deliveryCount: count,
            replayed: true,
          },
          202,
          { 'Idempotent-Replayed': 'true' },
        );
      }
    }
    const ts = nowIso(db.current);
    const event = {
      id: nextId(db.current),
      type: body.type,
      payloadJson: json,
      createdAt: ts,
      idempotencyKey: key,
    };
    db.current.events.push(event);
    const targets = db.current.endpoints.filter(
      (e) =>
        (e.status === 'ACTIVE' || e.status === 'PAUSED') &&
        filterMatches(e.eventTypeFilter, event.type),
    );
    for (const t of targets) {
      db.current.deliveries.push({
        id: nextId(db.current),
        eventId: event.id,
        endpointId: t.id,
        status: 'PENDING',
        attemptCount: 0,
        attemptBase: 0,
        nextAttemptAt: ts,
        lastStatusCode: null,
        createdAt: ts,
        updatedAt: ts,
      });
    }
    return send(
      PublishEventResponseSchema,
      {
        id: event.id,
        type: event.type,
        createdAt: ts,
        deliveryCount: targets.length,
        replayed: false,
      },
      202,
    );
  }),

  http.get(`${API}/events/:id`, ({ request, params }) => {
    const denied = requireKey(request);
    if (denied) return denied;
    const e = db.current.events.find((x) => x.id === params.id);
    if (!e) return apiError(request, 404, 'NOT_FOUND', 'Event not found');
    return send(EventResponseSchema, {
      id: e.id,
      type: e.type,
      payload: JSON.parse(e.payloadJson) as z.infer<typeof EventResponseSchema>['payload'],
      createdAt: e.createdAt,
      deliveries: summaries(e.id),
    });
  }),
];

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

const deliveryHandlers = [
  http.get(`${API}/deliveries`, ({ request }) => {
    const denied = requireKey(request);
    if (denied) return denied;
    const q = new URL(request.url).searchParams;
    const rawLimit = q.get('limit');
    const limit = rawLimit === null ? 50 : Number(rawLimit);
    if (!Number.isInteger(limit) || limit < 1 || limit > MAX_DELIVERY_PAGE) {
      return validation(request, 'limit', 'limit must be between 1 and 200');
    }
    const rawStatus = q.get('status')?.trim().toUpperCase() ?? '';
    if (rawStatus !== '' && !(DELIVERY_STATUSES as readonly string[]).includes(rawStatus)) {
      return validation(
        request,
        'status',
        'status must be one of PENDING, IN_FLIGHT, SUCCEEDED, DEAD',
      );
    }
    const endpointId = q.get('endpointId');
    if (endpointId && !UUID_RE.test(endpointId)) return malformed(request);
    const cursorRaw = q.get('cursor');
    const cursor = cursorRaw ? decodeCursor(cursorRaw) : null;
    if (cursorRaw && !cursor) return validation(request, 'cursor', 'invalid cursor');

    const rows = db.current.deliveries
      .filter((d) => rawStatus === '' || d.status === (rawStatus as DeliveryStatus))
      .filter((d) => !endpointId || d.endpointId === endpointId)
      .filter((d) => !cursor || isOlderThanCursor(d, cursor))
      .sort(compareNewestFirst)
      .slice(0, limit + 1);
    let nextCursor: string | null = null;
    if (rows.length > limit) {
      rows.pop();
      const last = rows[rows.length - 1];
      nextCursor = last ? encodeCursor(last) : null;
    }
    return send(DeliveryPageSchema, { items: rows, nextCursor });
  }),

  http.get(`${API}/deliveries/:id`, ({ request, params }) => {
    const denied = requireKey(request);
    if (denied) return denied;
    const d = db.current.deliveries.find((x) => x.id === params.id);
    return d
      ? send(DeliveryDetailSchema, d)
      : apiError(request, 404, 'NOT_FOUND', 'Delivery not found');
  }),

  http.get(`${API}/deliveries/:id/attempts`, ({ request, params }) => {
    const denied = requireKey(request);
    if (denied) return denied;
    const id = String(params.id);
    if (!db.current.deliveries.some((x) => x.id === id))
      return apiError(request, 404, 'NOT_FOUND', 'Delivery not found');
    return send(AttemptListSchema, db.current.attempts.get(id) ?? []);
  }),

  http.post(`${API}/deliveries/:id/replay`, ({ request, params }) => {
    const denied = requireKey(request);
    if (denied) return denied;
    const d = db.current.deliveries.find((x) => x.id === params.id);
    if (!d) return apiError(request, 404, 'NOT_FOUND', 'Delivery not found');
    if (d.status !== 'DEAD' && d.status !== 'SUCCEEDED') {
      return apiError(
        request,
        409,
        'CONFLICT',
        `Only DEAD or SUCCEEDED deliveries can be replayed (status is ${d.status})`,
      );
    }
    const ts = nowIso(db.current);
    Object.assign(d, {
      status: 'PENDING',
      attemptBase: d.attemptCount,
      nextAttemptAt: ts,
      updatedAt: ts,
    });
    const endpoint = db.current.endpoints.find((e) => e.id === d.endpointId);
    const message =
      endpoint?.status === 'PAUSED'
        ? 'Replay queued, but the endpoint is PAUSED, so it will be sent after the endpoint is resumed.'
        : endpoint?.status === 'DISABLED'
          ? 'Replay queued, but the endpoint was deleted, so it will not be sent.'
          : 'Replay queued.';
    return send(ReplayResponseSchema, { delivery: d, message }, 202);
  }),
];

export const handlers = [...endpointHandlers, ...eventHandlers, ...deliveryHandlers];
