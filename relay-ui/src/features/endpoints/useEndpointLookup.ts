import { useMemo } from 'react';
import type { Endpoint } from '@/api/schemas';
import { useEndpoints } from './queries';

/** id -> endpoint, for showing URLs next to deliveries. Deleted endpoints are absent (the API hides them). */
export function useEndpointLookup(): (id: string) => Endpoint | undefined {
  const { data } = useEndpoints();
  const map = useMemo(() => new Map((data ?? []).map((e) => [e.id, e])), [data]);
  return (id) => map.get(id);
}
