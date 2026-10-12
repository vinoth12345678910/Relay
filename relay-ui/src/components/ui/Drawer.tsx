import * as RD from '@radix-ui/react-dialog';
import { X } from 'lucide-react';
import type { ReactNode } from 'react';

/** Side sheet on Radix Dialog (focus trap, Escape, focus return). Full-width on phones. */
export function Drawer({
  open,
  onOpenChange,
  title,
  children,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: ReactNode;
  children: ReactNode;
}) {
  return (
    <RD.Root open={open} onOpenChange={onOpenChange}>
      <RD.Portal>
        <RD.Overlay className="fixed inset-0 z-40 bg-black/40" />
        <RD.Content
          aria-describedby={undefined}
          className="fixed inset-y-0 right-0 z-50 flex w-full max-w-2xl flex-col border-l border-border bg-surface shadow-2xl"
        >
          <div className="flex items-center justify-between gap-4 border-b border-border px-5 py-3">
            <RD.Title className="min-w-0 text-base font-semibold">{title}</RD.Title>
            <RD.Close
              className="rounded-md p-1 text-muted hover:bg-surface-2 hover:text-fg"
              aria-label="Close details"
            >
              <X aria-hidden className="size-4" />
            </RD.Close>
          </div>
          <div className="flex-1 overflow-y-auto px-5 py-4">{children}</div>
        </RD.Content>
      </RD.Portal>
    </RD.Root>
  );
}
