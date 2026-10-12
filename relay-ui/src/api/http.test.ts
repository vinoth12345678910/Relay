import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { server } from '@/mocks/node';
import { session } from '@/session/store';
import { TEST_KEY, connectTestSession } from '@/test/render';
import { listEndpoints, publishEvent } from './client';
import { describeError } from './errors';
import { ApiError, ContractError, NetworkError, buildUrl } from './http';

describe('request wrapper', () => {
  it('sends the API key as a Bearer header and never in the URL', async () => {
    connectTestSession();
    const seen: { auth: string | null; url: string }[] = [];
    server.events.on('request:start', ({ request }) => {
      seen.push({ auth: request.headers.get('Authorization'), url: request.url });
    });
    await listEndpoints();
    server.events.removeAllListeners();
    expect(seen[0]?.auth).toBe(`Bearer ${TEST_KEY}`);
    expect(seen.every((s) => !s.url.includes(TEST_KEY))).toBe(true);
  });

  it('parses the ApiError body, including field errors', async () => {
    connectTestSession();
    const err = await publishEvent({ type: 'Bad Type', payload: {} }).catch((e: unknown) => e);
    expect(err).toBeInstanceOf(ApiError);
    const api = err as ApiError;
    expect(api.status).toBe(400);
    expect(api.code).toBe('VALIDATION_ERROR');
    expect(api.fieldErrors).toEqual([
      { field: 'type', message: 'type must match ^[a-z0-9._-]{1,100}$' },
    ]);
  });

  it('on 401 clears the key, leaves a notice and does not retry', async () => {
    session.connect({ apiKey: 'rl_live_revoked_key', baseUrl: '', remember: true });
    let calls = 0;
    server.events.on('request:start', () => calls++);
    await expect(listEndpoints()).rejects.toBeInstanceOf(ApiError);
    server.events.removeAllListeners();
    expect(calls).toBe(1);
    expect(session.get()).toBeNull();
    expect(window.sessionStorage.length).toBe(0);
    expect(session.takeNotice()).toMatch(/rejected the API key/);
  });

  it('turns a response that breaks the contract into a ContractError with a readable message', async () => {
    connectTestSession();
    server.use(http.get('*/api/v1/endpoints', () => HttpResponse.json([{ id: 'x', url: 1 }])));
    const err = await listEndpoints().catch((e: unknown) => e);
    expect(err).toBeInstanceOf(ContractError);
    const d = describeError(err);
    expect(d.title).toBe('Unexpected response from Relay');
    expect(d.detail).not.toContain('{');
  });

  it('maps a non-JSON proxy error (backend down) to a friendly message', async () => {
    connectTestSession();
    server.use(
      http.get('*/api/v1/endpoints', () => new HttpResponse('<html>502</html>', { status: 502 })),
    );
    const err = await listEndpoints().catch((e: unknown) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(describeError(err).detail).toMatch(/Is relay-server running/);
  });

  it('maps a network failure to NetworkError', async () => {
    connectTestSession();
    server.use(http.get('*/api/v1/endpoints', () => HttpResponse.error()));
    await expect(listEndpoints()).rejects.toBeInstanceOf(NetworkError);
  });

  it('builds URLs against the origin or a custom base, dropping empty query params', () => {
    expect(
      buildUrl('', '/api/v1/deliveries', { status: 'DEAD', cursor: undefined, endpointId: '' }),
    ).toBe(`${window.location.origin}/api/v1/deliveries?status=DEAD`);
    expect(buildUrl('https://relay.example.com/base/', '/api/v1/events')).toBe(
      'https://relay.example.com/base/api/v1/events',
    );
  });
});

describe('stale 401', () => {
  it('a late 401 for an old key does not wipe a newer session', async () => {
    session.connect({ apiKey: 'rl_live_revoked_old', baseUrl: '', remember: false });
    const pending = listEndpoints().catch((e: unknown) => e);
    session.connect({ apiKey: 'rl_live_new_key', baseUrl: '', remember: false });
    expect(await pending).toBeInstanceOf(ApiError);
    expect(session.get()?.apiKey).toBe('rl_live_new_key');
  });
});
