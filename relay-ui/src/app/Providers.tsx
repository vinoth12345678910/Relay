import { useEffect, useState, type ReactNode } from 'react';
import { QueryClientProvider, type QueryClient } from '@tanstack/react-query';
import { ToastProvider } from '@/components/ui/Toast';
import { TooltipProvider } from '@/components/ui/Tooltip';
import { ClockProvider, systemClock, type Clock } from '@/lib/clock';
import { session } from '@/session/store';
import { createQueryClient } from './queryClient';

/** Drops every cached response when the session ends, so the next key never sees the old app's data. */
function useClearCacheOnDisconnect(client: QueryClient) {
  useEffect(() => {
    let prev = session.get();
    return session.subscribe(() => {
      const next = session.get();
      if (prev !== null && (next === null || next.apiKey !== prev.apiKey)) client.clear();
      prev = next;
    });
  }, [client]);
}

export function Providers({
  children,
  queryClient,
  clock = systemClock,
}: {
  children: ReactNode;
  queryClient?: QueryClient;
  clock?: Clock;
}) {
  const [client] = useState(() => queryClient ?? createQueryClient());
  useClearCacheOnDisconnect(client);
  return (
    <QueryClientProvider client={client}>
      <ClockProvider clock={clock}>
        <TooltipProvider delayDuration={200}>
          <ToastProvider>{children}</ToastProvider>
        </TooltipProvider>
      </ClockProvider>
    </QueryClientProvider>
  );
}
