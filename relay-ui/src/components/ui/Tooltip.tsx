import * as RT from '@radix-ui/react-tooltip';
import type { ReactNode } from 'react';

export const TooltipProvider = RT.Provider;

/** Hover/focus tooltip. The trigger must be focusable so keyboard users get it too. */
export function Tooltip({ content, children }: { content: ReactNode; children: ReactNode }) {
  return (
    <RT.Root delayDuration={200}>
      <RT.Trigger asChild>{children}</RT.Trigger>
      <RT.Portal>
        <RT.Content
          sideOffset={4}
          className="z-[70] rounded-md border border-border bg-surface px-2 py-1 text-xs text-fg shadow-md"
        >
          {content}
        </RT.Content>
      </RT.Portal>
    </RT.Root>
  );
}
