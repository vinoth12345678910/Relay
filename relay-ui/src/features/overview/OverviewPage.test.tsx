import { act, screen, waitFor, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { server } from '@/mocks/node';
import { addDeliveries, resetDb } from '@/mocks/seed';
import { renderApp } from '@/test/render';

function setVisibility(state: 'visible' | 'hidden') {
  Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => state });
  document.dispatchEvent(new Event('visibilitychange'));
}

function countPendingRequests() {
  const counter = { n: 0 };
  server.events.on('request:start', ({ request }) => {
    if (request.url.includes('status=PENDING')) counter.n++;
  });
  return counter;
}

afterEach(() => {
  server.events.removeAllListeners();
  setVisibility('visible');
  vi.useRealTimers();
});

describe('Overview', () => {
  it('shows counts, paused endpoints with reasons, queue health and recent dead deliveries', async () => {
    renderApp('/');
    expect(await screen.findByTestId('count-DEAD')).toHaveTextContent('2');
    expect(screen.getByTestId('count-PENDING')).toHaveTextContent('2');
    const paused = screen.getByRole('region', { name: /Paused endpoints/ });
    expect(within(paused).getByText(/Circuit breaker opened/)).toBeInTheDocument();
    const health = screen.getByRole('region', { name: 'Queue health' });
    expect(within(health).getByText('Waiting on paused endpoints').nextSibling).toHaveTextContent(
      '2',
    );
    const dead = screen.getByRole('region', { name: 'Recent dead deliveries' });
    expect(within(dead).getAllByRole('button', { name: 'Replay' })).toHaveLength(2);
    expect(screen.getByText(/Relay has no stats endpoint/)).toBeInTheDocument();
  });

  it('labels capped counts as "200+"', async () => {
    addDeliveries(205, 'SUCCEEDED');
    renderApp('/');
    expect(await screen.findByTestId('count-SUCCEEDED')).toHaveTextContent('200+');
  });

  it('resumes a paused endpoint in one click', async () => {
    const { user } = renderApp('/');
    const paused = await screen.findByRole('region', { name: /Paused endpoints/ });
    await user.click(within(paused).getByRole('button', { name: 'Resume Billing' }));
    await waitFor(() =>
      expect(within(paused).getByText(/All endpoints are delivering/)).toBeInTheDocument(),
    );
  });

  it('shows an empty state with a next step', async () => {
    resetDb(false);
    renderApp('/');
    expect(await screen.findByText('Nothing here yet')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Add an endpoint' })).toHaveAttribute(
      'href',
      '/endpoints',
    );
  });

  it('auto-refreshes every 10 s, can be paused, and stops while the tab is hidden', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const counter = countPendingRequests();
    const { user } = renderApp('/', { advanceTimers: vi.advanceTimersByTime });
    await screen.findByTestId('count-PENDING');
    expect(screen.getByTestId('last-updated')).toHaveTextContent(/Updated/);
    const initial = counter.n;

    await act(() => vi.advanceTimersByTimeAsync(10_000));
    await waitFor(() => expect(counter.n).toBe(initial + 1));

    await user.click(screen.getByRole('button', { name: 'Pause auto-refresh' }));
    expect(screen.getByRole('button', { name: 'Resume auto-refresh' })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
    await act(() => vi.advanceTimersByTimeAsync(30_000));
    expect(counter.n).toBe(initial + 1);

    await user.click(screen.getByRole('button', { name: 'Resume auto-refresh' }));
    act(() => setVisibility('hidden'));
    expect(screen.getByText(/waits while this tab is hidden/)).toBeInTheDocument();
    await act(() => vi.advanceTimersByTimeAsync(30_000));
    expect(counter.n).toBe(initial + 1);

    act(() => setVisibility('visible'));
    await act(() => vi.advanceTimersByTimeAsync(10_000));
    await waitFor(() => expect(counter.n).toBeGreaterThan(initial + 1));
  });
});
