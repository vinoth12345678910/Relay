import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  createEndpoint,
  deleteEndpoint,
  listEndpoints,
  pauseEndpoint,
  resumeEndpoint,
  updateEndpoint,
} from '@/api/client';
import type { CreateEndpointRequest, Endpoint, UpdateEndpointRequest } from '@/api/schemas';

/** Query keys never contain credentials; the cache is cleared on disconnect (see Providers). */
export const endpointKeys = {
  all: ['endpoints'] as const,
};

export function useEndpoints() {
  return useQuery({ queryKey: endpointKeys.all, queryFn: ({ signal }) => listEndpoints(signal) });
}

/** Replaces one endpoint in the cached list (or drops it), then refetches in the background. */
function useEndpointCache() {
  const qc = useQueryClient();
  return {
    upsert(e: Endpoint) {
      qc.setQueryData<Endpoint[]>(endpointKeys.all, (list) =>
        list
          ? list.some((x) => x.id === e.id)
            ? list.map((x) => (x.id === e.id ? e : x))
            : [...list, e]
          : list,
      );
      void qc.invalidateQueries({ queryKey: endpointKeys.all });
    },
    remove(id: string) {
      qc.setQueryData<Endpoint[]>(endpointKeys.all, (list) => list?.filter((x) => x.id !== id));
      void qc.invalidateQueries({ queryKey: endpointKeys.all });
    },
  };
}

/**
 * Creates an endpoint. The response carries the one-time signing secret: it is split off inside
 * mutationFn and handed straight to `onCreated`, so it never enters TanStack's query or mutation
 * caches (a mutation's `data` otherwise lingers in the MutationCache until garbage collection).
 */
export function useCreateEndpoint(onCreated: (endpoint: Endpoint, signingSecret: string) => void) {
  const cache = useEndpointCache();
  return useMutation({
    mutationFn: async (body: CreateEndpointRequest): Promise<Endpoint> => {
      const { signingSecret, ...endpoint } = await createEndpoint(body);
      onCreated(endpoint, signingSecret);
      return endpoint;
    },
    onSuccess: (endpoint) => cache.upsert(endpoint),
  });
}

export function useUpdateEndpoint() {
  const cache = useEndpointCache();
  return useMutation({
    mutationFn: (args: { id: string; body: UpdateEndpointRequest }) =>
      updateEndpoint(args.id, args.body),
    onSuccess: (e) => cache.upsert(e),
  });
}

export function usePauseEndpoint() {
  const cache = useEndpointCache();
  return useMutation({
    mutationFn: (args: { id: string; reason?: string }) => pauseEndpoint(args.id, args.reason),
    onSuccess: (e) => cache.upsert(e),
  });
}

export function useResumeEndpoint() {
  const cache = useEndpointCache();
  return useMutation({
    mutationFn: (id: string) => resumeEndpoint(id),
    onSuccess: (e) => cache.upsert(e),
  });
}

export function useDeleteEndpoint() {
  const cache = useEndpointCache();
  return useMutation({
    mutationFn: (id: string) => deleteEndpoint(id),
    onSuccess: (_d, id) => cache.remove(id),
  });
}
