import { request } from './http';
import {
  AttemptListSchema,
  CreateAppResponseSchema,
  CreateEndpointResponseSchema,
  DeliveryDetailSchema,
  DeliveryPageSchema,
  EndpointListSchema,
  EndpointResponseSchema,
  EventResponseSchema,
  PublishEventResponseSchema,
  ReplayResponseSchema,
  type CreateEndpointRequest,
  type DeliveryStatus,
  type UpdateEndpointRequest,
} from './schemas';

/** Typed calls for every Relay route the UI uses. Paths and verbs mirror the @RestControllers. */

const V1 = '/api/v1';

// ---------- apps ----------

export async function createApp(args: { name: string; adminToken: string; baseUrl: string }) {
  const res = await request({
    method: 'POST',
    path: `${V1}/apps`,
    body: { name: args.name },
    schema: CreateAppResponseSchema,
    auth: { kind: 'admin', token: args.adminToken },
    baseUrl: args.baseUrl,
  });
  return res.data;
}

/** Checks a candidate key before it is stored: any authenticated GET works; endpoints is cheap. */
export async function verifyApiKey(args: { apiKey: string; baseUrl: string }) {
  await request({
    path: `${V1}/endpoints`,
    schema: EndpointListSchema,
    auth: { kind: 'explicitKey', apiKey: args.apiKey, baseUrl: args.baseUrl },
  });
}

// ---------- endpoints ----------

export async function listEndpoints(signal?: AbortSignal) {
  return (await request({ path: `${V1}/endpoints`, schema: EndpointListSchema, signal })).data;
}

export async function createEndpoint(body: CreateEndpointRequest) {
  return (
    await request({
      method: 'POST',
      path: `${V1}/endpoints`,
      body,
      schema: CreateEndpointResponseSchema,
    })
  ).data;
}

export async function updateEndpoint(id: string, body: UpdateEndpointRequest) {
  return (
    await request({
      method: 'PATCH',
      path: `${V1}/endpoints/${encodeURIComponent(id)}`,
      body,
      schema: EndpointResponseSchema,
    })
  ).data;
}

export async function deleteEndpoint(id: string) {
  await request({ method: 'DELETE', path: `${V1}/endpoints/${encodeURIComponent(id)}` });
}

export async function pauseEndpoint(id: string, reason?: string) {
  return (
    await request({
      method: 'POST',
      path: `${V1}/endpoints/${encodeURIComponent(id)}/pause`,
      // The body is optional server-side; a blank reason becomes "Paused manually".
      body: reason && reason.trim() !== '' ? { reason: reason.trim() } : undefined,
      schema: EndpointResponseSchema,
    })
  ).data;
}

export async function resumeEndpoint(id: string) {
  return (
    await request({
      method: 'POST',
      path: `${V1}/endpoints/${encodeURIComponent(id)}/resume`,
      schema: EndpointResponseSchema,
    })
  ).data;
}

// ---------- events ----------

export async function publishEvent(args: {
  type: string;
  payload: unknown;
  idempotencyKey?: string;
}) {
  const key = args.idempotencyKey?.trim();
  const res = await request({
    method: 'POST',
    path: `${V1}/events`,
    body: { type: args.type, payload: args.payload },
    headers: key ? { 'Idempotency-Key': key } : undefined,
    schema: PublishEventResponseSchema,
  });
  // The header is the documented signal; the body flag is the same fact and survives a
  // cross-origin setup where the header is not exposed.
  const replayed = res.headers.get('Idempotent-Replayed') === 'true' || res.data.replayed;
  return { ...res.data, replayed };
}

export async function getEvent(id: string, signal?: AbortSignal) {
  return (
    await request({
      path: `${V1}/events/${encodeURIComponent(id)}`,
      schema: EventResponseSchema,
      signal,
    })
  ).data;
}

// ---------- deliveries ----------

export type DeliveryFilters = { status?: DeliveryStatus; endpointId?: string };

export async function listDeliveries(
  args: DeliveryFilters & { cursor?: string | null; limit?: number },
  signal?: AbortSignal,
) {
  return (
    await request({
      path: `${V1}/deliveries`,
      query: {
        status: args.status,
        endpointId: args.endpointId,
        cursor: args.cursor,
        limit: args.limit,
      },
      schema: DeliveryPageSchema,
      signal,
    })
  ).data;
}

export async function getDelivery(id: string, signal?: AbortSignal) {
  return (
    await request({
      path: `${V1}/deliveries/${encodeURIComponent(id)}`,
      schema: DeliveryDetailSchema,
      signal,
    })
  ).data;
}

export async function listAttempts(id: string, signal?: AbortSignal) {
  return (
    await request({
      path: `${V1}/deliveries/${encodeURIComponent(id)}/attempts`,
      schema: AttemptListSchema,
      signal,
    })
  ).data;
}

export async function replayDelivery(id: string) {
  return (
    await request({
      method: 'POST',
      path: `${V1}/deliveries/${encodeURIComponent(id)}/replay`,
      schema: ReplayResponseSchema,
    })
  ).data;
}
