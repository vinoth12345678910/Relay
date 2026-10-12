import { screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { db } from '@/mocks/db';
import { apiError } from '@/mocks/handlers';
import { server } from '@/mocks/node';
import { addDeliveries } from '@/mocks/seed';
import { renderApp, testLocation } from '@/test/render';

const rowsIn = (table: HTMLElement) => within(table).getAllByRole('row').slice(1);
const deliveryByStatus = (s: string) => db.current.deliveries.find((d) => d.status === s)!;

describe('Deliveries list', () => {
  it('syncs filters to the URL and sends them to the API', async () => {
    const queries: string[] = [];
    server.events.on('request:start', ({ request }) => {
      if (request.url.includes('/deliveries')) queries.push(new URL(request.url).search);
    });
    const { user } = renderApp('/deliveries');
    const table = await screen.findByRole('table', { name: /Deliveries/ });
    expect(rowsIn(table)).toHaveLength(6);

    await user.selectOptions(screen.getByLabelText('Status'), 'DEAD');
    await waitFor(() => expect(testLocation()).toBe('/deliveries?status=DEAD'));
    await waitFor(() =>
      expect(rowsIn(screen.getByRole('table', { name: /Deliveries/ }))).toHaveLength(2),
    );
    expect(queries.at(-1)).toContain('status=DEAD');

    const orders = db.current.endpoints[0]!;
    await user.selectOptions(screen.getByLabelText('Endpoint'), orders.id);
    await waitFor(() =>
      expect(testLocation()).toBe(`/deliveries?status=DEAD&endpointId=${orders.id}`),
    );
    await waitFor(() =>
      expect(rowsIn(screen.getByRole('table', { name: /Deliveries/ }))).toHaveLength(1),
    );
    server.events.removeAllListeners();
  });

  it('restores filters from the URL on load', async () => {
    renderApp('/deliveries?status=PENDING');
    await waitFor(() => expect(screen.getByLabelText('Status')).toHaveValue('PENDING'));
    const table = await screen.findByRole('table', { name: /Deliveries/ });
    expect(within(table).getAllByText('Pending')).toHaveLength(2);
  });

  it('loads more with the cursor, never duplicates rows, and hides the button at the end', async () => {
    addDeliveries(110);
    const { user } = renderApp('/deliveries');
    const table = await screen.findByRole('table', { name: /Deliveries/ });
    expect(rowsIn(table)).toHaveLength(50);
    addDeliveries(5); // newer rows arrive while reading: keyset paging must not shift
    await user.click(screen.getByRole('button', { name: 'Load more' }));
    await waitFor(() => expect(rowsIn(table)).toHaveLength(100));
    await user.click(screen.getByRole('button', { name: 'Load more' }));
    await waitFor(() => expect(rowsIn(table)).toHaveLength(116));
    expect(screen.queryByRole('button', { name: 'Load more' })).not.toBeInTheDocument();
    expect(screen.getByText(/116 shown, end of list/)).toBeInTheDocument();
    const ids = within(table)
      .getAllByTitle(/^[0-9a-f-]{36}$/)
      .map((el) => el.getAttribute('title'));
    expect(new Set(ids).size).toBe(ids.length);
  });

  it('shows a filtered empty state', async () => {
    renderApp(`/deliveries?endpointId=${db.current.endpoints[2]!.id}`);
    expect(await screen.findByText('No deliveries match these filters')).toBeInTheDocument();
  });
});

describe('Delivery drawer', () => {
  it('opens from the URL with ids, budget, and an attempts timeline', async () => {
    const dead = deliveryByStatus('DEAD');
    renderApp(`/deliveries?delivery=${dead.id}`);
    const drawer = await screen.findByRole('dialog', { name: /Delivery/ });
    expect(await within(drawer).findByText('Attempt 1')).toBeInTheDocument();
    expect(within(drawer).getByText('Attempt 9')).toBeInTheDocument();
    expect(within(drawer).getAllByText('HTTP 502').length).toBeGreaterThan(0);
    expect(within(drawer).getByText(/attemptBase 0: never replayed/)).toBeInTheDocument();
  });

  it('renders response snippets as escaped text, never as HTML', async () => {
    const dead = deliveryByStatus('DEAD');
    renderApp(`/deliveries?delivery=${dead.id}`);
    const drawer = await screen.findByRole('dialog', { name: /Delivery/ });
    const snippet = await within(drawer).findByRole('region', {
      name: 'Response snippet of attempt 1',
    });
    expect(snippet.textContent).toContain('<script>alert(1)</script>');
    expect(snippet.querySelector('script')).toBeNull();
    expect(snippet.querySelector('h1')).toBeNull();
    expect(snippet.innerHTML).toContain('&lt;script&gt;');
    expect(document.querySelectorAll('script')).toHaveLength(0);
  });

  it('disables replay for in-flight and pending deliveries, with the reason', async () => {
    renderApp(`/deliveries?delivery=${deliveryByStatus('IN_FLIGHT').id}`);
    const drawer = await screen.findByRole('dialog', { name: /Delivery/ });
    const button = await within(drawer).findByRole('button', { name: 'Replay' });
    expect(button).toBeDisabled();
    expect(button).toHaveAccessibleDescription(
      'Only Dead or Succeeded deliveries can be replayed.',
    );
  });

  it('replays a DEAD delivery after confirmation and shows the backend message (paused endpoint)', async () => {
    const billing = db.current.endpoints[1]!;
    const dead = db.current.deliveries.find(
      (d) => d.status === 'DEAD' && d.endpointId === billing.id,
    )!;
    const { user } = renderApp(`/deliveries?delivery=${dead.id}`);
    const drawer = await screen.findByRole('dialog', { name: /Delivery/ });
    await user.click(await within(drawer).findByRole('button', { name: 'Replay' }));
    const confirm = await screen.findByRole('dialog', { name: 'Replay this delivery?' });
    await user.click(within(confirm).getByRole('button', { name: 'Replay' }));
    expect(
      await within(drawer).findByText(
        /endpoint is PAUSED, so it will be sent after the endpoint is resumed/,
      ),
    ).toBeInTheDocument();
    await waitFor(() => expect(within(drawer).getByText('Pending')).toBeInTheDocument());
    expect(within(drawer).getByRole('button', { name: 'Replay' })).toBeDisabled();
  });

  it('handles a 409 (already replayed elsewhere) gracefully', async () => {
    const dead = deliveryByStatus('DEAD');
    server.use(
      http.post('*/api/v1/deliveries/:id/replay', ({ request }) =>
        apiError(
          request,
          409,
          'CONFLICT',
          'Only DEAD or SUCCEEDED deliveries can be replayed (status is PENDING)',
        ),
      ),
    );
    const { user } = renderApp(`/deliveries?delivery=${dead.id}`);
    const drawer = await screen.findByRole('dialog', { name: /Delivery/ });
    await user.click(await within(drawer).findByRole('button', { name: 'Replay' }));
    await user.click(
      within(await screen.findByRole('dialog', { name: 'Replay this delivery?' })).getByRole(
        'button',
        { name: 'Replay' },
      ),
    );
    expect(
      await within(drawer).findByText(/status is PENDING\). It may already have been replayed/),
    ).toBeInTheDocument();
  });

  it('closing the drawer removes it from the URL', async () => {
    const dead = deliveryByStatus('DEAD');
    const { user } = renderApp(`/deliveries?status=DEAD&delivery=${dead.id}`);
    const drawer = await screen.findByRole('dialog', { name: /Delivery/ });
    await user.click(within(drawer).getByRole('button', { name: 'Close details' }));
    await waitFor(() => expect(testLocation()).toBe('/deliveries?status=DEAD'));
  });

  it('shows a friendly error for an unknown delivery id', async () => {
    server.use(
      http.get('*/api/v1/deliveries/:id', ({ request }) =>
        apiError(request, 404, 'NOT_FOUND', 'Delivery not found'),
      ),
    );
    renderApp('/deliveries?delivery=00000000-0000-4000-8000-00000000ffff');
    expect(
      await screen.findByText(/Delivery not found\. It may have been deleted/),
    ).toBeInTheDocument();
  });

  it('a garbled list response shows a friendly contract error, not a crash', async () => {
    server.use(
      http.get('*/api/v1/deliveries', () =>
        HttpResponse.json({ items: [{ id: 1 }], nextCursor: null }),
      ),
    );
    renderApp('/deliveries');
    expect(await screen.findByText('Unexpected response from Relay')).toBeInTheDocument();
  });
});
