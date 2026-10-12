import { screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { server } from '@/mocks/node';
import { session } from '@/session/store';
import { renderApp, testLocation } from '@/test/render';

const KEY = 'rl_live_connect_test_key_abc';

describe('Connect screen', () => {
  it('redirects to Connect when there is no session', async () => {
    renderApp('/endpoints', { connected: false });
    expect(await screen.findByRole('heading', { name: 'Connect to Relay' })).toBeInTheDocument();
    expect(testLocation()).toBe('/connect');
  });

  it('validates the form before calling the API', async () => {
    const { user } = renderApp('/connect', { connected: false });
    await user.type(screen.getByLabelText(/API base URL/), 'ftp://nope');
    await user.click(screen.getByRole('button', { name: 'Connect' }));
    expect(await screen.findByText(/Enter an http\(s\) URL/)).toBeInTheDocument();
    expect(screen.getByText('Enter the API key for your app')).toBeInTheDocument();
    expect(screen.getByLabelText('API key')).toHaveAttribute('aria-invalid', 'true');
  });

  it('connects with a valid key, keeps it in memory only, and lands on the page it came from', async () => {
    const { user } = renderApp('/endpoints', { connected: false });
    await user.type(await screen.findByLabelText('API key'), KEY);
    await user.click(screen.getByRole('button', { name: 'Connect' }));
    expect(await screen.findByRole('heading', { name: 'Endpoints' })).toBeInTheDocument();
    expect(testLocation()).toBe('/endpoints');
    expect(session.get()?.apiKey).toBe(KEY);
    expect(window.sessionStorage.length).toBe(0);
    expect(window.localStorage.length).toBe(0);
  });

  it('"Remember on this device" uses sessionStorage, never localStorage; Disconnect wipes it', async () => {
    const { user } = renderApp('/connect', { connected: false });
    await user.type(screen.getByLabelText('API key'), KEY);
    await user.click(screen.getByRole('checkbox', { name: /Remember on this device/ }));
    await user.click(screen.getByRole('button', { name: 'Connect' }));
    await screen.findByRole('heading', { name: 'Endpoints' });
    expect(window.sessionStorage.getItem('relay-ui.session')).toContain(KEY);
    expect(JSON.stringify({ ...window.localStorage })).not.toContain(KEY);

    await user.click(screen.getByRole('button', { name: /Disconnect/ }));
    expect(await screen.findByRole('heading', { name: 'Connect to Relay' })).toBeInTheDocument();
    expect(window.sessionStorage.length).toBe(0);
    expect(session.get()).toBeNull();
  });

  it('shows a field error when the key is rejected, without storing it', async () => {
    const { user } = renderApp('/connect', { connected: false });
    await user.type(screen.getByLabelText('API key'), 'rl_live_revoked_key');
    await user.click(screen.getByRole('button', { name: 'Connect' }));
    expect(await screen.findByText(/Relay rejected this API key/)).toBeInTheDocument();
    expect(session.get()).toBeNull();
  });

  it('a 401 during use sends the user back to Connect with an explanation', async () => {
    session.connect({ apiKey: 'rl_live_revoked_later', baseUrl: '', remember: false });
    renderApp('/endpoints', { connected: false });
    expect(await screen.findByRole('heading', { name: 'Connect to Relay' })).toBeInTheDocument();
    expect(screen.getByText(/rejected the API key \(401\)/)).toBeInTheDocument();
  });

  it('creates an app with the admin token, shows the key once, and never keeps the token', async () => {
    const consoleSpy = vi.spyOn(console, 'log');
    const urls: string[] = [];
    server.events.on('request:start', ({ request }) => urls.push(request.url));
    const { user } = renderApp('/connect', { connected: false });
    await user.type(screen.getByLabelText('App name'), 'shop');
    const token = screen.getByLabelText('Admin token');
    expect(token).toHaveAttribute('type', 'password');
    await user.type(token, 'dev-admin-token');
    await user.click(screen.getByRole('button', { name: 'Create app' }));

    const dialog = await screen.findByRole('dialog', { name: /App "shop" created/ });
    const key = within(dialog).getByTestId('one-time-api-key').textContent ?? '';
    expect(key).toMatch(/^rl_live_/);
    expect(within(dialog).getByText(/You will not see this key again/)).toBeInTheDocument();
    expect(screen.getByLabelText('Admin token')).toHaveValue('');

    await user.click(within(dialog).getByRole('button', { name: /I saved it, close/ }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(screen.queryByText(key)).not.toBeInTheDocument();
    expect(urls.some((u) => u.includes('dev-admin-token') || u.includes(key))).toBe(false);
    expect(consoleSpy).not.toHaveBeenCalled();
    expect(window.localStorage.length).toBe(0);
    expect(window.sessionStorage.length).toBe(0);
    server.events.removeAllListeners();
  });

  it('a wrong admin token is reported on the field and cleared', async () => {
    const { user } = renderApp('/connect', { connected: false });
    await user.type(screen.getByLabelText('App name'), 'shop');
    await user.type(screen.getByLabelText('Admin token'), 'wrong');
    await user.click(screen.getByRole('button', { name: 'Create app' }));
    expect(await screen.findByText('Relay rejected the admin token.')).toBeInTheDocument();
    expect(screen.getByLabelText('Admin token')).toHaveValue('');
  });
});
