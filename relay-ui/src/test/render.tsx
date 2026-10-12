import { render } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, useLocation } from 'react-router';
import { Providers } from '@/app/Providers';
import { createQueryClient } from '@/app/queryClient';
import { AppRoutes } from '@/app/routes';
import type { Clock } from '@/lib/clock';
import { session } from '@/session/store';

export const TEST_KEY = 'rl_live_test_key_0123456789abcdef';
/** 2026-10-12T10:00:00Z: an hour after the mock seed timestamps. */
export const FIXED_NOW = Date.parse('2026-10-12T10:00:00.000Z');
export const fixedClock: Clock = { now: () => FIXED_NOW };

let currentLocation = '';
function LocationSpy() {
  const l = useLocation();
  currentLocation = `${l.pathname}${l.search}`;
  return null;
}
/** Path + query of the in-memory router, for asserting URL state. */
export const testLocation = () => currentLocation;

export function connectTestSession(apiKey = TEST_KEY) {
  session.connect({ apiKey, baseUrl: '', remember: false });
}

/** Renders the whole app (real routes, providers, MSW-backed API) at `route`. */
export function renderApp(route: string, opts: { connected?: boolean; clock?: Clock } = {}) {
  if (opts.connected ?? true) connectTestSession();
  const user = userEvent.setup();
  const queryClient = createQueryClient();
  queryClient.setDefaultOptions({
    queries: { ...queryClient.getDefaultOptions().queries, retry: false },
  });
  const utils = render(
    <MemoryRouter initialEntries={[route]}>
      <Providers queryClient={queryClient} clock={opts.clock ?? fixedClock}>
        <LocationSpy />
        <AppRoutes />
      </Providers>
    </MemoryRouter>,
  );
  return { ...utils, user, queryClient };
}
