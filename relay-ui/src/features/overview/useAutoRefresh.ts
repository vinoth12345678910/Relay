import { useState, useSyncExternalStore } from 'react';

function subscribeVisibility(cb: () => void) {
  document.addEventListener('visibilitychange', cb);
  return () => document.removeEventListener('visibilitychange', cb);
}
const isVisible = () => document.visibilityState !== 'hidden';

/**
 * Polling switch for the overview: returns the refetch interval to hand to TanStack Query, or
 * false when the user paused it or the tab is hidden (no requests from a background tab).
 */
export function useAutoRefresh(intervalMs: number) {
  const [paused, setPaused] = useState(false);
  const visible = useSyncExternalStore(subscribeVisibility, isVisible, () => true);
  return {
    paused,
    visible,
    toggle: () => setPaused((p) => !p),
    refetchInterval: !paused && visible ? intervalMs : (false as const),
  };
}
