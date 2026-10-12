import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { listDeliveries } from '@/api/client';
import type { DeliveryStatus } from '@/api/schemas';
import { useEndpoints } from '@/features/endpoints/queries';
import { useClock } from '@/lib/clock';
import { SAMPLE_SIZE } from './derive';

export const overviewKeys = {
  all: ['overview'] as const,
  status: (s: DeliveryStatus) => ['overview', 'status', s] as const,
};

/** The newest SAMPLE_SIZE deliveries of one status (Relay has no count/stats endpoint). */
function useStatusSample(status: DeliveryStatus, refetchInterval: number | false) {
  return useQuery({
    queryKey: overviewKeys.status(status),
    queryFn: ({ signal }) => listDeliveries({ status, limit: SAMPLE_SIZE }, signal),
    refetchInterval,
    refetchIntervalInBackground: false,
  });
}

/** One capped page per status plus the endpoint list, all refreshed on the same interval. */
export function useOverviewData(refetchInterval: number | false) {
  const clock = useClock();
  const endpoints = useEndpoints({ refetchInterval });
  const byStatus = {
    PENDING: useStatusSample('PENDING', refetchInterval),
    IN_FLIGHT: useStatusSample('IN_FLIGHT', refetchInterval),
    SUCCEEDED: useStatusSample('SUCCEEDED', refetchInterval),
    DEAD: useStatusSample('DEAD', refetchInterval),
  };
  const all = [endpoints, ...Object.values(byStatus)];
  const updatedAt = Math.max(...all.map((q) => q.dataUpdatedAt));

  // "Last updated" is stamped with the injected clock (TanStack uses the real one) when data lands.
  const [lastUpdated, setLastUpdated] = useState<number | null>(null);
  useEffect(() => {
    if (updatedAt > 0) setLastUpdated(clock.now());
  }, [updatedAt, clock]);

  return {
    endpoints,
    byStatus,
    lastUpdated,
    isFetching: all.some((q) => q.isFetching),
    refetchAll: () => all.forEach((q) => void q.refetch()),
  };
}
