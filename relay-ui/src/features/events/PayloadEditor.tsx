import { forwardRef, type TextareaHTMLAttributes } from 'react';
import { CircleCheck, CircleAlert } from 'lucide-react';
import { MAX_PAYLOAD_BYTES } from '@/api/schemas';
import { formatBytes } from '@/lib/bytes';
import { cn } from '@/lib/cn';
import { sizeVerdict, type PayloadAnalysis } from '@/lib/json';

/** Live JSON status + size against the 256 KB limit, announced politely to screen readers. */
export function PayloadStatus({ analysis: a }: { analysis: PayloadAnalysis }) {
  if (!a.ok) {
    return (
      <p aria-live="polite" className="flex items-center gap-1.5 text-xs text-danger">
        <CircleAlert aria-hidden className="size-3.5" /> {a.error}
      </p>
    );
  }
  const { ratio, over, near } = sizeVerdict(a.compactBytes, MAX_PAYLOAD_BYTES);
  return (
    <div aria-live="polite" className="flex flex-col gap-1">
      <p className="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs">
        <span className="flex items-center gap-1.5 text-success">
          <CircleCheck aria-hidden className="size-3.5" /> Valid JSON
        </span>
        <span
          className={cn(
            'tabular-nums',
            over ? 'text-danger' : near ? 'text-warning' : 'text-muted',
          )}
        >
          {formatBytes(a.compactBytes)} of 256 KB (compact)
        </span>
      </p>
      <div aria-hidden className="h-1 w-full overflow-hidden rounded-full bg-surface-2">
        <div
          className={cn('h-full', over ? 'bg-danger' : near ? 'bg-warning' : 'bg-accent')}
          style={{ width: `${Math.min(100, ratio * 100)}%` }}
        />
      </div>
      {over && (
        <p role="alert" className="text-xs font-medium text-danger">
          Over Relay&apos;s 256 KB limit: the server will reject this with 413. Send less data or a
          reference to it.
        </p>
      )}
      {near && !over && <p className="text-xs text-warning">Close to the 256 KB limit.</p>}
    </div>
  );
}

export const PayloadTextarea = forwardRef<
  HTMLTextAreaElement,
  TextareaHTMLAttributes<HTMLTextAreaElement>
>(function PayloadTextarea({ className, ...props }, ref) {
  return (
    <textarea
      ref={ref}
      rows={12}
      spellCheck={false}
      autoComplete="off"
      className={cn(
        'w-full rounded-md border border-border-strong bg-code-bg p-3 font-mono text-[12.5px] leading-relaxed text-fg',
        'aria-[invalid=true]:border-danger',
        className,
      )}
      {...props}
    />
  );
});
