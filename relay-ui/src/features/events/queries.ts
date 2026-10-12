import { isId } from '@/lib/json';
import { useMutation, useQuery } from '@tanstack/react-query';
import { getEvent, publishEvent } from '@/api/client';
import { recentEvents } from './recentEvents';

export const eventKeys = {
  all: ['event'] as const,
  detail: (id: string) => ['event', id] as const,
};

export function useEvent(id: string) {
  return useQuery({
    queryKey: eventKeys.detail(id),
    queryFn: ({ signal }) => getEvent(id, signal),
    enabled: isId(id),
  });
}

export function usePublishEvent() {
  return useMutation({
    mutationFn: (args: { type: string; payload: unknown; idempotencyKey?: string }) =>
      publishEvent(args),
    onSuccess: (e) => recentEvents.add(e),
  });
}
