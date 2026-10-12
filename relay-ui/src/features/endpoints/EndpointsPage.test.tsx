import { screen, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { server } from '@/mocks/node';
import { resetDb } from '@/mocks/seed';
import { renderApp } from '@/test/render';

describe('Endpoints list (MSW integration)', () => {
  it('renders endpoints from the API with status text and paused reason', async () => {
    renderApp('/endpoints');
    const table = await screen.findByRole('table', { name: 'Endpoints' });
    const rows = within(table).getAllByRole('row');
    expect(rows).toHaveLength(4); // header + 3 seeded
    expect(within(table).getByText('https://hooks.example.com/orders')).toBeInTheDocument();
    expect(within(table).getByText('Paused')).toBeInTheDocument();
    expect(within(table).getByText(/Circuit breaker opened/)).toBeInTheDocument();
  });

  it('shows an empty state with a next step', async () => {
    resetDb(false);
    renderApp('/endpoints');
    expect(await screen.findByText('No endpoints yet')).toBeInTheDocument();
  });

  it('shows a readable error and retries on demand', async () => {
    server.use(
      http.get('*/api/v1/endpoints', () =>
        HttpResponse.json(
          {
            status: 500,
            code: 'INTERNAL_ERROR',
            message: 'boom',
            error: 'x',
            path: '/',
            timestamp: '2026-01-01T00:00:00Z',
          },
          { status: 500 },
        ),
      ),
    );
    renderApp('/endpoints');
    expect(await screen.findByText('Relay had an internal error')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Retry/ })).toBeInTheDocument();
  });

  it('shows a loading state first', () => {
    renderApp('/endpoints');
    expect(screen.getByRole('status', { name: 'Loading endpoints' })).toBeInTheDocument();
  });
});
