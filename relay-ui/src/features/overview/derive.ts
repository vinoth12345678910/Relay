import { MAX_DELIVERY_PAGE, type Delivery, type DeliveryPage, type Endpoint } from '@/api/schemas';

/** Count from one capped page: exact when the page is the whole set, "at least" otherwise. */
export type CappedCount = { value: number; capped: boolean };

export function countFromPage(page: DeliveryPage): CappedCount {
  return { value: page.items.length, capped: page.nextCursor !== null };
}

export const formatCount = (c: CappedCount) => (c.capped ? `${c.value}+` : String(c.value));

export const OVERDUE_GRACE_MS = 60_000;

export type QueueHealth = {
  /** PENDING deliveries whose time has come, to endpoints that are not paused, among the sample. */
  overdue: number;
  /** How late the most overdue one is, in ms (null if none). */
  oldestOverdueMs: number | null;
  /** PENDING deliveries waiting on a paused endpoint: expected to wait. */
  waitingOnPaused: number;
  /** PENDING deliveries whose endpoint was deleted: the claimer skips them, they will never send. */
  orphaned: number;
  /** True when the sample was capped, so numbers are lower bounds. */
  sampled: boolean;
  state: 'healthy' | 'backlog' | 'idle';
};

/**
 * Queue health derived from the newest PENDING deliveries (Relay has no stats endpoint).
 * Deliveries to PAUSED endpoints are excluded from "overdue": holding them is the breaker working.
 * Deliveries to endpoints missing from the list (deleted, so DISABLED) are counted apart: the
 * backend only claims deliveries of ACTIVE endpoints, so they stay PENDING forever by design.
 */
export function deriveQueueHealth(
  pending: DeliveryPage,
  endpoints: Endpoint[],
  now: number,
): QueueHealth {
  const status = new Map(endpoints.map((e) => [e.id, e.status]));
  let overdue = 0;
  let waitingOnPaused = 0;
  let orphaned = 0;
  let oldest: number | null = null;
  for (const d of pending.items) {
    const s = status.get(d.endpointId);
    if (s === 'PAUSED') {
      waitingOnPaused++;
      continue;
    }
    if (s === undefined || s === 'DISABLED') {
      orphaned++;
      continue;
    }
    const late = now - Date.parse(d.nextAttemptAt);
    if (late > OVERDUE_GRACE_MS) {
      overdue++;
      oldest = oldest === null ? late : Math.max(oldest, late);
    }
  }
  const state = pending.items.length === 0 ? 'idle' : overdue > 0 ? 'backlog' : 'healthy';
  return {
    overdue,
    oldestOverdueMs: oldest,
    waitingOnPaused,
    orphaned,
    sampled: pending.nextCursor !== null,
    state,
  };
}

export function recentDead(dead: DeliveryPage, n = 5): Delivery[] {
  return dead.items.slice(0, n);
}

export const SAMPLE_SIZE = MAX_DELIVERY_PAGE;

export function formatAge(ms: number): string {
  const s = Math.round(ms / 1000);
  if (s < 60) return `${s}s`;
  const m = Math.round(s / 60);
  if (m < 60) return `${m} min`;
  const h = Math.floor(m / 60);
  return h < 48 ? `${h} h ${m % 60} min` : `${Math.floor(h / 24)} days`;
}
