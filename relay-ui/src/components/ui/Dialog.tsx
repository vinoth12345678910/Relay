import * as RD from '@radix-ui/react-dialog';
import { X } from 'lucide-react';
import type { ReactNode } from 'react';
import { useReturnFocus } from './useReturnFocus';
import { cn } from '@/lib/cn';

/**
 * Modal dialog on Radix: focus is trapped while open and returned to the trigger on close;
 * Escape and the close button dismiss it unless `dismissible` is false.
 */
export function Dialog({
  open,
  onOpenChange,
  title,
  description,
  children,
  footer,
  dismissible = true,
  className,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  description?: ReactNode;
  children?: ReactNode;
  footer?: ReactNode;
  dismissible?: boolean;
  className?: string;
}) {
  const focus = useReturnFocus();
  return (
    <RD.Root open={open} onOpenChange={onOpenChange}>
      <RD.Portal>
        <RD.Overlay className="fixed inset-0 z-40 bg-black/50 backdrop-blur-[1px]" />
        <RD.Content
          onOpenAutoFocus={focus.onOpenAutoFocus}
          onCloseAutoFocus={focus.onCloseAutoFocus}
          onEscapeKeyDown={(e) => !dismissible && e.preventDefault()}
          onPointerDownOutside={(e) => !dismissible && e.preventDefault()}
          onInteractOutside={(e) => !dismissible && e.preventDefault()}
          className={cn(
            'fixed top-1/2 left-1/2 z-50 flex max-h-[90vh] w-[calc(100vw-2rem)] max-w-lg -translate-x-1/2 -translate-y-1/2 flex-col',
            'rounded-xl border border-border bg-surface shadow-2xl',
            className,
          )}
          {...(description ? {} : { 'aria-describedby': undefined })}
        >
          <div className="flex items-start justify-between gap-4 border-b border-border px-5 py-4">
            <div className="flex flex-col gap-1">
              <RD.Title className="text-base font-semibold">{title}</RD.Title>
              {description && (
                <RD.Description className="text-sm text-muted">{description}</RD.Description>
              )}
            </div>
            {dismissible && (
              <RD.Close
                className="rounded-md p-1 text-muted hover:bg-surface-2 hover:text-fg"
                aria-label="Close"
              >
                <X aria-hidden className="size-4" />
              </RD.Close>
            )}
          </div>
          <div className="overflow-y-auto px-5 py-4">{children}</div>
          {footer && (
            <div className="flex flex-wrap justify-end gap-2 border-t border-border px-5 py-3">
              {footer}
            </div>
          )}
        </RD.Content>
      </RD.Portal>
    </RD.Root>
  );
}
