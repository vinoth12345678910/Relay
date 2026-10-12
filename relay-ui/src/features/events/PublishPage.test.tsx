import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { server } from '@/mocks/node';
import { renderApp } from '@/test/render';

const KEY = '11111111-2222-4333-8444-555555555555';

async function setup() {
  vi.spyOn(crypto, 'randomUUID').mockReturnValue(KEY);
  const ctx = renderApp('/events/new');
  await screen.findByRole('heading', { name: 'Publish an event' });
  const publish = () => ctx.user.click(screen.getByRole('button', { name: /Publish event/ }));
  const setPayload = (text: string) =>
    fireEvent.change(screen.getByLabelText('Payload (JSON)'), { target: { value: text } });
  const setType = async (t: string) => {
    const input = screen.getByLabelText('Event type');
    await ctx.user.clear(input);
    await ctx.user.type(input, t);
  };
  return { ...ctx, publish, setPayload, setType };
}

describe('Publish event', () => {
  it('publishes without a key and shows id + deliveryCount, no replay banner', async () => {
    const { publish } = await setup();
    await publish();
    const result = await screen.findByRole('region', { name: /Accepted/ });
    expect(within(result).getByTestId('delivery-count')).toHaveTextContent('2');
    expect(within(result).queryByText(/Idempotent replay/)).not.toBeInTheDocument();
    expect(within(result).getByRole('link', { name: /View event/ })).toBeInTheDocument();
  });

  it('generates a key only on request and shows the replayed banner on a repeat publish', async () => {
    const { user, publish } = await setup();
    expect(screen.getByLabelText(/Idempotency-Key/)).toHaveValue('');
    expect(crypto.randomUUID).not.toHaveBeenCalled();
    await user.click(screen.getByRole('button', { name: 'Generate idempotency key' }));
    expect(screen.getByLabelText(/Idempotency-Key/)).toHaveValue(KEY);

    const headers: (string | null)[] = [];
    server.events.on('request:start', ({ request }) =>
      headers.push(request.headers.get('Idempotency-Key')),
    );
    await publish();
    const first = await screen.findByRole('region', { name: 'Accepted · 202' });
    const firstId = within(first).getByTitle(/^[0-9a-f-]{36}$/).textContent;
    await publish();
    const second = await screen.findByRole('region', { name: /Accepted \(replayed\)/ });
    expect(within(second).getByText(/Idempotent replay/)).toBeInTheDocument();
    expect(within(second).getByTitle(firstId!)).toBeInTheDocument();
    expect(headers).toEqual([KEY, KEY]);
    server.events.removeAllListeners();
  });

  it('warns when a replay returns the original type (backend ignores type for idempotency)', async () => {
    const { user, publish, setType } = await setup();
    await user.click(screen.getByRole('button', { name: 'Generate idempotency key' }));
    await publish();
    await screen.findByRole('region', { name: 'Accepted · 202' });
    await setType('order.shipped');
    await publish();
    expect(
      await screen.findByText(/Relay compares only the payload for idempotency/),
    ).toBeInTheDocument();
  });

  it('explains a 409 when the key was used with a different payload', async () => {
    const { user, publish, setPayload } = await setup();
    await user.click(screen.getByRole('button', { name: 'Generate idempotency key' }));
    await publish();
    await screen.findByRole('region', { name: 'Accepted · 202' });
    setPayload('{"orderId": 99}');
    await publish();
    expect(await screen.findByText(/already used with a different payload/)).toBeInTheDocument();
    expect(screen.getByLabelText(/Idempotency-Key/)).toHaveAttribute('aria-invalid', 'true');
  });

  it('warns over 256 KB and explains the 413 the server returns', async () => {
    const { publish, setPayload } = await setup();
    setPayload(JSON.stringify({ blob: 'x'.repeat(256 * 1024) }));
    expect(await screen.findByText(/Over Relay's 256 KB limit/)).toBeInTheDocument();
    await publish();
    expect(await screen.findByText(/rejected the payload as too large/)).toBeInTheDocument();
  });

  it('flags invalid JSON live and does not call the API', async () => {
    const { publish, setPayload } = await setup();
    let calls = 0;
    server.events.on('request:start', ({ request }) => {
      if (request.url.includes('/events')) calls++;
    });
    setPayload('{"orderId": ');
    expect(await screen.findAllByText(/Invalid JSON/)).not.toHaveLength(0);
    await publish();
    await waitFor(() =>
      expect(screen.getByLabelText('Payload (JSON)')).toHaveAttribute('aria-invalid', 'true'),
    );
    expect(calls).toBe(0);
    server.events.removeAllListeners();
  });

  it('validates the type like the backend', async () => {
    const { publish, setType } = await setup();
    await setType('Order Paid');
    await publish();
    expect(await screen.findByText(/lowercase letters, digits/)).toBeInTheDocument();
  });
});

describe('Event detail', () => {
  it('shows the payload as text and each delivery with its endpoint URL', async () => {
    const { user, publish } = await setup();
    await publish();
    await user.click(await screen.findByRole('link', { name: /View event/ }));
    expect(await screen.findByRole('heading', { name: 'order.paid' })).toBeInTheDocument();
    expect(screen.getByRole('region', { name: /Event payload/ })).toHaveTextContent(
      '"orderId": 42',
    );
    const table = screen.getByRole('table', { name: 'Deliveries of this event' });
    expect(await within(table).findByText('https://hooks.example.com/orders')).toBeInTheDocument();
    expect(within(table).getAllByText('Pending')).toHaveLength(2);
  });

  it('rejects a malformed id without calling the API', async () => {
    let calls = 0;
    server.events.on('request:start', ({ request }) => {
      if (request.url.includes('/events/')) calls++;
    });
    renderApp('/events/not-a-uuid');
    expect(await screen.findByText('Not a valid event id')).toBeInTheDocument();
    expect(calls).toBe(0);
    server.events.removeAllListeners();
  });

  it('shows a friendly not-found for an unknown id', async () => {
    renderApp('/events/00000000-0000-4000-8000-0000000fffff');
    expect(await screen.findByText('Not found')).toBeInTheDocument();
    expect(screen.getByText(/belongs to another app/)).toBeInTheDocument();
  });

  it('lists events published from this tab and clears them on disconnect', async () => {
    const { user, publish } = await setup();
    await publish();
    await screen.findByRole('region', { name: /Accepted/ });
    const recent = screen.getByRole('region', { name: 'Published from this tab' });
    expect(within(recent).getByRole('link', { name: 'order.paid' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /Disconnect/ }));
    await screen.findByRole('heading', { name: 'Connect to Relay' });
    const { recentEvents } = await import('./recentEvents');
    expect(recentEvents.get()).toEqual([]);
  });
});
