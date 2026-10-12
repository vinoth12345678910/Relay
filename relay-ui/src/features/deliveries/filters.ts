import { DELIVERY_STATUSES, type DeliveryStatus } from '@/api/schemas';

export type DeliveryFilterState = { status?: DeliveryStatus; endpointId?: string };

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** Reads filters from the query string, ignoring values the API would reject with a 400. */
export function filtersFromParams(params: URLSearchParams): DeliveryFilterState {
  const out: DeliveryFilterState = {};
  const status = params.get('status')?.toUpperCase();
  if (status && (DELIVERY_STATUSES as readonly string[]).includes(status))
    out.status = status as DeliveryStatus;
  const endpointId = params.get('endpointId');
  if (endpointId && UUID.test(endpointId)) out.endpointId = endpointId;
  return out;
}

/** Writes one filter into the query string, keeping unrelated params (like the open drawer). */
export function withFilter(
  params: URLSearchParams,
  key: 'status' | 'endpointId',
  value: string,
): URLSearchParams {
  const next = new URLSearchParams(params);
  if (value === '') next.delete(key);
  else next.set(key, value);
  return next;
}

export function withDelivery(params: URLSearchParams, id: string | null): URLSearchParams {
  const next = new URLSearchParams(params);
  if (id === null) next.delete('delivery');
  else next.set('delivery', id);
  return next;
}

/** Concatenates cursor pages, dropping any id already seen (e.g. a page refetched after new rows). */
export function mergePages<T extends { id: string }>(pages: ReadonlyArray<{ items: T[] }>): T[] {
  const seen = new Set<string>();
  const out: T[] = [];
  for (const p of pages) {
    for (const item of p.items) {
      if (!seen.has(item.id)) {
        seen.add(item.id);
        out.push(item);
      }
    }
  }
  return out;
}

export const canReplay = (status: DeliveryStatus) => status === 'DEAD' || status === 'SUCCEEDED';
