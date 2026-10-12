import type { z } from 'zod';
import { ApiErrorBodySchema, type ApiErrorBody, type FieldError } from './schemas';
import { session } from '@/session/store';

/** A non-2xx answer from Relay (or from the proxy in front of it). */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fieldErrors: FieldError[];
  /** Parsed ApiError body when the server sent one; null for proxy/HTML errors. */
  readonly body: ApiErrorBody | null;

  constructor(status: number, body: ApiErrorBody | null) {
    super(body?.message ?? `HTTP ${status}`);
    this.name = 'ApiError';
    this.status = status;
    this.code = body?.code ?? `HTTP_${status}`;
    this.fieldErrors = body?.fieldErrors ?? [];
    this.body = body;
  }
}

/** The request never got an HTTP answer (backend down, DNS, CORS, offline). */
export class NetworkError extends Error {
  constructor(readonly baseUrl: string) {
    super('Network request failed');
    this.name = 'NetworkError';
  }
}

/** The server answered 2xx but the body does not match the contract this UI was built against. */
export class ContractError extends Error {
  constructor(
    readonly path: string,
    readonly issues: string[],
  ) {
    super('Unexpected response shape');
    this.name = 'ContractError';
  }
}

export type Auth =
  | { kind: 'apiKey' }
  | { kind: 'admin'; token: string }
  | { kind: 'explicitKey'; apiKey: string; baseUrl: string };

export type RequestOptions<T> = {
  method?: 'GET' | 'POST' | 'PATCH' | 'DELETE';
  path: string;
  query?: Record<string, string | number | undefined | null>;
  body?: unknown;
  headers?: Record<string, string>;
  /** Omit only for 204 responses. */
  schema?: z.ZodType<T>;
  auth?: Auth;
  /** For the admin call, which runs before any session exists. */
  baseUrl?: string;
  signal?: AbortSignal;
};

export type ApiResponse<T> = { data: T; status: number; headers: Headers };

const UNAUTHORIZED_NOTICE = 'Relay rejected the API key (401). It has been cleared; connect again.';

export function buildUrl(
  baseUrl: string,
  path: string,
  query?: RequestOptions<unknown>['query'],
): string {
  const origin = typeof window !== 'undefined' ? window.location.origin : 'http://localhost';
  // A custom base keeps its own path prefix (https://host/relay + /api/v1/...).
  const base = baseUrl.trim() === '' ? origin : baseUrl.trim().replace(/\/+$/, '');
  const url = new URL(`${base}${path}`);
  for (const [k, v] of Object.entries(query ?? {})) {
    if (v !== undefined && v !== null && v !== '') url.searchParams.set(k, String(v));
  }
  return url.toString();
}

function resolveAuth(auth: Auth): { token: string; baseUrl: string; isApiKey: boolean } | null {
  switch (auth.kind) {
    case 'admin':
      return { token: auth.token, baseUrl: '', isApiKey: false };
    case 'explicitKey':
      return { token: auth.apiKey, baseUrl: auth.baseUrl, isApiKey: false };
    case 'apiKey': {
      const s = session.get();
      return s ? { token: s.apiKey, baseUrl: s.baseUrl, isApiKey: true } : null;
    }
  }
}

async function readJson(res: Response): Promise<unknown> {
  const text = await res.text();
  if (text === '') return undefined;
  try {
    return JSON.parse(text);
  } catch {
    return undefined;
  }
}

/**
 * The single fetch wrapper. Adds the bearer header, parses errors into ApiError, validates
 * success bodies with zod, and on a 401 for an API-key call clears the session (no retry).
 * It never logs requests, headers or bodies.
 */
export async function request<T>(opts: RequestOptions<T>): Promise<ApiResponse<T>> {
  const auth = resolveAuth(opts.auth ?? { kind: 'apiKey' });
  if (auth === null) {
    throw new ApiError(401, null);
  }
  const baseUrl = opts.baseUrl ?? auth.baseUrl;
  const headers: Record<string, string> = {
    Accept: 'application/json',
    Authorization: `Bearer ${auth.token}`,
    ...opts.headers,
  };
  // Also on bodyless POSTs (pause/resume/replay): that is exactly what relay-server's integration
  // tests send, so it is the proven path for @RequestBody(required = false).
  if (opts.body !== undefined || (opts.method !== undefined && opts.method !== 'GET')) {
    headers['Content-Type'] = 'application/json';
  }

  let res: Response;
  try {
    res = await fetch(buildUrl(baseUrl, opts.path, opts.query), {
      method: opts.method ?? 'GET',
      headers,
      body: opts.body === undefined ? undefined : JSON.stringify(opts.body),
      signal: opts.signal,
      credentials: 'omit',
      cache: 'no-store',
      referrerPolicy: 'no-referrer',
    });
  } catch (e) {
    if (e instanceof DOMException && e.name === 'AbortError') throw e;
    throw new NetworkError(baseUrl);
  }

  if (!res.ok) {
    const parsed = ApiErrorBodySchema.safeParse(await readJson(res));
    const error = new ApiError(res.status, parsed.success ? parsed.data : null);
    // Only expire the session that sent this request: a late 401 for an old key must not
    // wipe a key the user connected with in the meantime.
    if (res.status === 401 && auth.isApiKey && session.get()?.apiKey === auth.token) {
      session.expire(UNAUTHORIZED_NOTICE);
    }
    throw error;
  }

  if (!opts.schema) {
    return { data: undefined as T, status: res.status, headers: res.headers };
  }
  const parsed = opts.schema.safeParse(await readJson(res));
  if (!parsed.success) {
    throw new ContractError(
      opts.path,
      parsed.error.issues.map((i) => `${i.path.join('.') || '(root)'}: ${i.message}`),
    );
  }
  return { data: parsed.data, status: res.status, headers: res.headers };
}
