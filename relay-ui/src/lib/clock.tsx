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

/** Current time in ms, refreshed every `tickMs` so relative labels stay fresh. */
export function useNow(tickMs = 30_000): number {
  const clock = useClock();
  const [now, setNow] = useState(() => clock.now());
  useEffect(() => {
    setNow(clock.now());
    const t = setInterval(() => setNow(clock.now()), tickMs);
    return () => clearInterval(t);
  }, [clock, tickMs]);
  return now;
}
