import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { getDelivery, listAttempts, listDeliveries, replayDelivery } from '@/api/client';
import { eventKeys } from '@/features/events/queries';
import { overviewKeys } from '@/features/overview/queries';
import type { DeliveryFilterState } from './filters';

export const PAGE_SIZE = 50;

export const deliveryKeys = {
  all: ['deliveries'] as const,
  list: (f: DeliveryFilterState) =>
    ['deliveries', 'list', f.status ?? null, f.endpointId ?? null] as const,
  detail: (id: string) => ['deliveries', 'detail', id] as const,
  attempts: (id: string) => ['deliveries', 'attempts', id] as const,
};

/** Cursor pagination only: the API has no page numbers or totals. */
export function useDeliveryList(filters: DeliveryFilterState) {
  return useInfiniteQuery({
    queryKey: deliveryKeys.list(filters),
    initialPageParam: null as string | null,
    queryFn: ({ pageParam, signal }) =>
      listDeliveries({ ...filters, cursor: pageParam, limit: PAGE_SIZE }, signal),
    getNextPageParam: (last) => last.nextCursor ?? undefined,
  });
}

export function useDelivery(id: string | null) {
  return useQuery({
    queryKey: deliveryKeys.detail(id ?? ''),
    queryFn: ({ signal }) => getDelivery(id ?? '', signal),
    enabled: id !== null,
  });
}

export function useAttempts(id: string | null) {
  return useQuery({
    queryKey: deliveryKeys.attempts(id ?? ''),
    queryFn: ({ signal }) => listAttempts(id ?? '', signal),
    enabled: id !== null,
  });
}

/** Replays a DEAD/SUCCEEDED delivery, then refreshes every view that could show it. */
export function useReplay() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => replayDelivery(id),
    onSuccess: (r) => {
      qc.setQueryData(deliveryKeys.detail(r.delivery.id), r.delivery);
    },
    onSettled: () => {
      void qc.invalidateQueries({ queryKey: deliveryKeys.all });
      void qc.invalidateQueries({ queryKey: eventKeys.all });
      void qc.invalidateQueries({ queryKey: overviewKeys.all });
    },
  });
}
