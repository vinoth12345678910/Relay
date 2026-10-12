import { useRef } from 'react';

/**
 * Radix restores focus only to a <Dialog.Trigger>. Our dialogs are opened from state, so this
 * remembers what had focus when the dialog opened and puts focus back there when it closes
 * (or on <main> if that element is gone, e.g. a list row that was replaced).
 */
export function useReturnFocus() {
  const opener = useRef<HTMLElement | null>(null);
  return {
    onOpenAutoFocus: () => {
      opener.current =
        document.activeElement instanceof HTMLElement ? document.activeElement : null;
    },
    onCloseAutoFocus: (e: Event) => {
      e.preventDefault();
      const target = opener.current?.isConnected ? opener.current : document.getElementById('main');
      target?.focus();
      opener.current = null;
    },
  };
}
