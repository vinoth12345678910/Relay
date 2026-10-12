import { screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { renderApp } from '@/test/render';

async function openCreate() {
  const ctx = renderApp('/endpoints');
  await screen.findByRole('table', { name: 'Endpoints' });
  await ctx.user.click(screen.getAllByRole('button', { name: /Add endpoint/ })[0]!);
  const dialog = await screen.findByRole('dialog', { name: 'Add endpoint' });
  return { ...ctx, dialog };
}

describe('Endpoint management', () => {
  it('shows the signing secret once, with the whsec_ decoding note, then forgets it', async () => {
    const { user, dialog, queryClient } = await openCreate();
    await user.type(within(dialog).getByLabelText('URL'), 'https://new.example.com/hook');
    await user.type(within(dialog).getByLabelText(/Event type filter/), 'order.*');
    await user.click(within(dialog).getByRole('button', { name: 'Add endpoint' }));

    const secretDialog = await screen.findByRole('dialog', { name: /copy the signing secret/ });
    const secret = within(secretDialog).getByTestId('one-time-secret').textContent ?? '';
    expect(secret).toMatch(/^whsec_/);
    expect(
      within(secretDialog).getByText(/You will not see this secret again/),
    ).toBeInTheDocument();
    expect(within(secretDialog).getByText(/base64url-decode/)).toBeInTheDocument();
    expect(
      within(secretDialog).getByRole('link', { name: /verifying-signatures\.md/ }),
    ).toHaveAttribute('rel', 'noopener noreferrer');
    // Not dismissible by Escape: the user must acknowledge.
    await user.keyboard('{Escape}');
    expect(screen.getByRole('dialog', { name: /copy the signing secret/ })).toBeInTheDocument();

    await user.click(
      within(secretDialog).getByRole('button', { name: 'I have stored the secret' }),
    );
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(document.body.textContent).not.toContain(secret);
    expect(await screen.findByText('https://new.example.com/hook')).toBeInTheDocument();
    // Gone from every cache, not just the screen.
    const caches = JSON.stringify([
      queryClient
        .getQueryCache()
        .getAll()
        .map((q) => q.state.data),
      queryClient
        .getMutationCache()
        .getAll()
        .map((m) => m.state.data),
    ]);
    expect(caches).not.toContain(secret);
    expect(JSON.stringify({ ...window.localStorage, ...window.sessionStorage })).not.toContain(
      secret,
    );
  });

  it('maps an SSRF rejection onto the URL field as readable text', async () => {
    const { user, dialog } = await openCreate();
    await user.type(
      within(dialog).getByLabelText('URL'),
      'http://169.254.169.254/latest/meta-data',
    );
    await user.click(within(dialog).getByRole('button', { name: 'Add endpoint' }));
    const error = await within(dialog).findByText(/must not target a private or loopback address/);
    expect(error).toHaveTextContent(/SSRF protection/);
    expect(within(dialog).getByLabelText('URL')).toHaveAttribute('aria-invalid', 'true');
    expect(dialog.textContent).not.toMatch(/[{}]|VALIDATION_ERROR|fieldErrors/);
  });

  it('blocks an invalid filter before sending and warns about a misplaced "*"', async () => {
    const { user, dialog } = await openCreate();
    await user.type(within(dialog).getByLabelText('URL'), 'https://x.example.com');
    const filter = within(dialog).getByLabelText(/Event type filter/);
    await user.type(filter, 'order*');
    expect(within(dialog).getByText(/matches the literal type "order\*" only/)).toBeInTheDocument();
    await user.clear(filter);
    await user.type(filter, 'Bad Filter');
    await user.click(within(dialog).getByRole('button', { name: 'Add endpoint' }));
    expect(await within(dialog).findByText(/Use lowercase letters, digits/)).toBeInTheDocument();
  });

  it('pauses with a reason, shows it, and resumes', async () => {
    const { user } = renderApp('/endpoints');
    await user.click(await screen.findByRole('button', { name: 'Pause Order service' }));
    const dialog = await screen.findByRole('dialog', { name: 'Pause endpoint' });
    await user.type(within(dialog).getByLabelText(/Reason/), 'receiver maintenance');
    await user.click(within(dialog).getByRole('button', { name: 'Pause' }));

    const row = (await screen.findByText('receiver maintenance')).closest('tr')!;
    expect(within(row).getByText('Paused')).toBeInTheDocument();
    await user.click(within(row).getByRole('button', { name: 'Resume Order service' }));
    await waitFor(() => expect(within(row).getByText('Active')).toBeInTheDocument());
    expect(within(row).queryByText('receiver maintenance')).not.toBeInTheDocument();
  });

  it('edits only what changed', async () => {
    const { user } = renderApp('/endpoints');
    await user.click(await screen.findByRole('button', { name: 'Edit Order service' }));
    const dialog = await screen.findByRole('dialog', { name: 'Edit endpoint' });
    const desc = within(dialog).getByLabelText(/Description/);
    await user.clear(desc);
    await user.type(desc, 'Orders v2');
    await user.click(within(dialog).getByRole('button', { name: 'Save changes' }));
    expect(await screen.findByText('Orders v2')).toBeInTheDocument();
  });

  it('soft-deletes after confirmation', async () => {
    const { user } = renderApp('/endpoints');
    await user.click(
      await screen.findByRole('button', { name: 'Delete https://audit.example.org/in' }),
    );
    const dialog = await screen.findByRole('dialog', { name: 'Delete endpoint?' });
    await user.click(within(dialog).getByRole('button', { name: 'Delete endpoint' }));
    await waitFor(() =>
      expect(screen.queryByText('https://audit.example.org/in')).not.toBeInTheDocument(),
    );
    expect(await screen.findByText('Endpoint deleted')).toBeInTheDocument();
  });
});
