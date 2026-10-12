import type { Attempt, Delivery, Endpoint, EndpointStatus } from '@/api/schemas';

/*
 * In-memory stand-in for Relay's database, used by the MSW handlers in tests and in
 * `npm run dev:mock`. Behaviour mirrors relay-server: soft delete, fan-out to ACTIVE+PAUSED,
 * filter matching, idempotency by payload hash (type ignored), keyset pagination.
 */

export type StoredEndpoint = Endpoint & { status: EndpointStatus };
export type StoredEvent = {
  id: string;
  type: string;
  payloadJson: string;
  createdAt: string;
  idempotencyKey: string | null;
};
export type Db = {
  endpoints: StoredEndpoint[];
  events: StoredEvent[];
  deliveries: Delivery[];
  attempts: Map<string, Attempt[]>;
  /** Monotonic clock for created/updated timestamps so ordering is deterministic. */
  tick: number;
  seq: number;
};

const BASE_TIME = Date.parse('2026-10-12T09:00:00.000Z');

export function mockId(n: number): string {
  // A valid v4-shaped UUID derived from a counter, so tests are deterministic.
  const hex = n.toString(16).padStart(12, '0');
  return `00000000-0000-4000-8000-${hex}`;
}

export function createDb(): Db {
  return { endpoints: [], events: [], deliveries: [], attempts: new Map(), tick: 0, seq: 0 };
}

export const db: { current: Db } = { current: createDb() };

export function nextId(d: Db): string {
  d.seq += 1;
  return mockId(d.seq);
}

export function nowIso(d: Db): string {
  d.tick += 1;
  return new Date(BASE_TIME + d.tick * 1000).toISOString();
}

/** EventTypeMatcher.matches */
export function filterMatches(filter: string, type: string): boolean {
  if (type.trim() === '') return false;
  const f = filter.trim();
  if (f === '' || f === '*') return true;
  if (f.endsWith('.*')) return type.startsWith(f.slice(0, -1));
  return f === type;
}

export function encodeCursor(d: Delivery): string {
  return btoa(`${d.createdAt}|${d.id}`).replace(/=+$/, '').replace(/\+/g, '-').replace(/\//g, '_');
}

export function decodeCursor(token: string): { createdAt: string; id: string } | null {
  try {
    const raw = atob(token.replace(/-/g, '+').replace(/_/g, '/'));
    const [createdAt, id] = raw.split('|');
    if (!createdAt || !id || Number.isNaN(Date.parse(createdAt))) return null;
    return { createdAt, id };
  } catch {
    return null;
  }
}

/** Newest first by (createdAt, id), like `ORDER BY d.created_at DESC, d.id DESC`. */
export function compareNewestFirst(a: Delivery, b: Delivery): number {
  if (a.createdAt !== b.createdAt) return a.createdAt < b.createdAt ? 1 : -1;
  return a.id < b.id ? 1 : a.id > b.id ? -1 : 0;
}

export function isOlderThanCursor(d: Delivery, c: { createdAt: string; id: string }): boolean {
  return d.createdAt < c.createdAt || (d.createdAt === c.createdAt && d.id < c.id);
}
