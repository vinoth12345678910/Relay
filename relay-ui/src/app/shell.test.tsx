import { render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { ErrorBoundary } from '@/components/ErrorBoundary';
import { renderApp } from '@/test/render';

describe('app shell', () => {
  it('sets a page title and moves focus to the content after navigation', async () => {
    const { user } = renderApp('/');
    await screen.findByRole('heading', { name: 'Overview' });
    expect(document.title).toBe('Overview · Relay Console');
    await user.click(screen.getByRole('link', { name: 'Deliveries' }));
    await screen.findByRole('heading', { name: 'Deliveries' });
    expect(document.title).toBe('Deliveries · Relay Console');
    expect(document.activeElement?.id).toBe('main');
  });

  it('returns focus to the row button after the drawer closes (keyboard flow)', async () => {
    const { user } = renderApp('/deliveries');
    const open = (await screen.findAllByRole('button', { name: /Open details/ }))[0]!;
    open.focus();
    await user.keyboard('{Enter}');
    await screen.findByRole('dialog', { name: /Delivery/ });
    await user.keyboard('{Escape}');
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    await waitFor(() =>
      expect(document.activeElement?.getAttribute('aria-label')).toBe(
        open.getAttribute('aria-label'),
      ),
    );
  });

  it('a rendering crash shows a recoverable error page instead of a white screen', async () => {
    const Boom = () => {
      throw new Error('render failed');
    };
    const spy = vi.spyOn(console, 'error').mockImplementation(() => {});
    render(
      <ErrorBoundary>
        <Boom />
      </ErrorBoundary>,
    );
    expect(screen.getByRole('alert')).toHaveTextContent('This page crashed');
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument();
    spy.mockRestore();
  });
});
