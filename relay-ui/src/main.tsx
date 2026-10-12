import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router';
import { Providers } from '@/app/Providers';
import { AppRoutes } from '@/app/routes';
import { session } from '@/session/store';
import './index.css';

async function boot() {
  // Statically false in a normal build, so the mock code is not even bundled.
  if (import.meta.env.VITE_USE_MOCKS === 'true') {
    const { startMockBackend } = await import('./mocks/browser');
    await startMockBackend();
  }
  session.hydrate();
  const root = document.getElementById('root');
  if (!root) throw new Error('#root missing');
  createRoot(root).render(
    <StrictMode>
      <BrowserRouter>
        <Providers>
          <AppRoutes />
        </Providers>
      </BrowserRouter>
    </StrictMode>,
  );
}

void boot();
