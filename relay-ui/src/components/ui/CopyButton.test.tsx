import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { CopyButton } from './CopyButton';

describe('CopyButton', () => {
  it('copies the value, announces it, and never puts the value in its label', async () => {
    const user = userEvent.setup();
    const write = vi.spyOn(navigator.clipboard, 'writeText').mockResolvedValue();
    render(<CopyButton value="whsec_secret" label="signing secret" />);
    const button = screen.getByRole('button', { name: 'Copy signing secret' });
    await user.click(button);
    expect(write).toHaveBeenCalledWith('whsec_secret');
    expect(await screen.findByText('Copied')).toBeInTheDocument();
    expect(button.outerHTML).not.toContain('whsec_secret');
  });

  it('tells the user when the clipboard is unavailable', async () => {
    const user = userEvent.setup();
    vi.spyOn(navigator.clipboard, 'writeText').mockRejectedValue(new Error('denied'));
    render(<CopyButton value="x" label="id" />);
    await user.click(screen.getByRole('button', { name: 'Copy id' }));
    expect(await screen.findByText(/Copy failed/)).toBeInTheDocument();
  });
});
