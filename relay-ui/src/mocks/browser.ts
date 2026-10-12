import { setupWorker } from 'msw/browser';
import { handlers } from './handlers';
import { resetDb } from './seed';

/** Starts the in-browser mock backend for `npm run dev:mock` and e2e. Seeded fresh on every load. */
export async function startMockBackend() {
  resetDb();
  await setupWorker(...handlers).start({ onUnhandledRequest: 'bypass', quiet: true });
}
