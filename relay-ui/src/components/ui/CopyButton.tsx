import { useEffect, useState } from 'react';
import { Check, Copy } from 'lucide-react';
import { cn } from '@/lib/cn';

/** Copies `value` to the clipboard. The label describes *what* is copied, never the value itself. */
export function CopyButton({
  value,
  label,
  className,
}: {
  value: string;
  label: string;
  className?: string;
}) {
  const [state, setState] = useState<'idle' | 'copied' | 'failed'>('idle');
  useEffect(() => {
    if (state === 'idle') return;
    const t = setTimeout(() => setState('idle'), 1800);
    return () => clearTimeout(t);
  }, [state]);

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(value);
      setState('copied');
    } catch {
      setState('failed');
    }
  };

  return (
    <button
      type="button"
      onClick={() => void copy()}
      aria-label={state === 'copied' ? `${label} copied` : `Copy ${label}`}
      title={`Copy ${label}`}
      className={cn(
        'inline-flex size-7 shrink-0 items-center justify-center rounded-md text-muted hover:bg-surface-2 hover:text-fg',
        className,
      )}
    >
      {state === 'copied' ? (
        <Check aria-hidden className="size-3.5 text-success" />
      ) : (
        <Copy aria-hidden className="size-3.5" />
      )}
      <span className="sr-only" aria-live="polite">
        {state === 'copied'
          ? 'Copied'
          : state === 'failed'
            ? 'Copy failed, select the text manually'
            : ''}
      </span>
    </button>
  );
}

/** A monospace id with a copy button, e.g. for delivery/event/endpoint ids. */
export function CopyableId({
  value,
  label,
  short = false,
}: {
  value: string;
  label: string;
  short?: boolean;
}) {
  return (
    <span className="inline-flex max-w-full min-w-0 items-center gap-0.5">
      <code className="truncate font-mono text-[12.5px]" title={value}>
        {short ? `${value.slice(0, 8)}…` : value}
      </code>
      <CopyButton value={value} label={label} />
    </span>
  );
}
