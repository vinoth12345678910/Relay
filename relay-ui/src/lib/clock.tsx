import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';

/** Time source for the UI. Components never call Date.now() directly, so tests can pin time. */
export type Clock = { now: () => number };

export const systemClock: Clock = { now: () => Date.now() };

const ClockContext = createContext<Clock>(systemClock);

export function ClockProvider({ clock, children }: { clock: Clock; children: ReactNode }) {
  return <ClockContext.Provider value={clock}>{children}</ClockContext.Provider>;
}

export function useClock(): Clock {
  return useContext(ClockContext);
}

/** One shared interval per tick length, however many components are listening. */
const tickers = new Map<
  number,
  { listeners: Set<() => void>; timer: ReturnType<typeof setInterval> }
>();

function subscribeTick(ms: number, listener: () => void): () => void {
  let t = tickers.get(ms);
  if (!t) {
    const listeners = new Set<() => void>();
    t = { listeners, timer: setInterval(() => listeners.forEach((l) => l()), ms) };
    tickers.set(ms, t);
  }
  t.listeners.add(listener);
  return () => {
    const cur = tickers.get(ms);
    if (!cur) return;
    cur.listeners.delete(listener);
    if (cur.listeners.size === 0) {
      clearInterval(cur.timer);
      tickers.delete(ms);
    }
  };
}

/** Current time in ms, refreshed every `tickMs` so relative labels stay fresh. */
export function useNow(tickMs = 30_000): number {
  const clock = useClock();
  const [now, setNow] = useState(() => clock.now());
  useEffect(() => {
    setNow(clock.now());
    return subscribeTick(tickMs, () => setNow(clock.now()));
  }, [clock, tickMs]);
  return now;
}
