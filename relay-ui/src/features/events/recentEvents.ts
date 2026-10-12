import { useSyncExternalStore } from 'react';
import type { PublishEventResponse } from '@/api/schemas';
import { session } from '@/session/store';

/**
 * Events published from this tab, newest first. Relay has no "list events" endpoint, so this is
 * the only list the UI can offer. Memory only; cleared on disconnect.
 */
export type RecentEvent = Pick<
  PublishEventResponse,
  'id' | 'type' | 'createdAt' | 'deliveryCount' | 'replayed'
>;

const MAX = 20;
let items: RecentEvent[] = [];
const listeners = new Set<() => void>();
const emit = () => listeners.forEach((l) => l());

session.subscribe(() => {
  if (session.get() === null && items.length > 0) {
    items = [];
    emit();
  }
});

export const recentEvents = {
  add(e: RecentEvent) {
    items = [e, ...items.filter((x) => x.id !== e.id)].slice(0, MAX);
    emit();
  },
  get: () => items,
  subscribe(l: () => void) {
    listeners.add(l);
    return () => {
      listeners.delete(l);
    };
  },
};

export function useRecentEvents(): RecentEvent[] {
  return useSyncExternalStore(recentEvents.subscribe, recentEvents.get, recentEvents.get);
}
