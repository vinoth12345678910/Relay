import { useQuery } from '@tanstack/react-query';
import { listEndpoints } from '@/api/client';

/** Query keys never contain credentials; the cache is cleared on disconnect (see Providers). */
export const endpointKeys = {
  all: ['endpoints'] as const,
};

export function useEndpoints() {
  return useQuery({ queryKey: endpointKeys.all, queryFn: ({ signal }) => listEndpoints(signal) });
}
