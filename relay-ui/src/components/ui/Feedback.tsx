import type { ReactNode } from 'react';
import { CircleAlert, Inbox, RotateCw } from 'lucide-react';
import { describeError } from '@/api/errors';
import { cn } from '@/lib/cn';
import { Button } from './Button';

export function Skeleton({ className }: { className?: string }) {
  return <div aria-hidden className={cn('animate-pulse rounded-md bg-surface-2', className)} />;
}

/** Placeholder rows for a table/list while the first load is in flight. */
export function ListSkeleton({ rows = 4, label }: { rows?: number; label: string }) {
  return (
    <div role="status" aria-label={label} className="flex flex-col gap-2">
      {Array.from({ length: rows }, (_, i) => (
        <Skeleton key={i} className="h-12 w-full" />
      ))}
      <span className="sr-only">{label}</span>
    </div>
  );
}

export function EmptyState({
  title,
  hint,
  action,
}: {
  title: string;
  hint?: ReactNode;
  action?: ReactNode;
}) {
  return (
    <div className="flex flex-col items-center gap-2 rounded-lg border border-dashed border-border-strong px-6 py-10 text-center">
      <Inbox aria-hidden className="size-6 text-muted" />
      <p className="font-medium">{title}</p>
      {hint && <p className="max-w-md text-sm text-muted">{hint}</p>}
      {action && <div className="mt-2">{action}</div>}
    </div>
  );
}

export function ErrorState({
  error,
  onRetry,
  className,
}: {
  error: unknown;
  onRetry?: () => void;
  className?: string;
}) {
  const d = describeError(error);
  return (
    <div
      role="alert"
      className={cn(
        'flex items-start gap-3 rounded-lg border border-danger/40 bg-danger-bg p-4',
        className,
      )}
    >
      <CircleAlert aria-hidden className="mt-0.5 size-5 shrink-0 text-danger" />
      <div className="flex min-w-0 flex-1 flex-col gap-1">
        <p className="font-medium text-danger">{d.title}</p>
        <p className="text-sm break-words text-fg">{d.detail}</p>
      </div>
      {onRetry && (
        <Button size="sm" onClick={onRetry}>
          <RotateCw aria-hidden className="size-3.5" /> Retry
        </Button>
      )}
    </div>
  );
}

export function Notice({
  tone = 'info',
  children,
  className,
}: {
  tone?: 'info' | 'warning' | 'success' | 'danger';
  children: ReactNode;
  className?: string;
}) {
  const tones = {
    info: 'border-info/30 bg-info-bg',
    warning: 'border-warning/40 bg-warning-bg',
    success: 'border-success/40 bg-success-bg',
    danger: 'border-danger/40 bg-danger-bg',
  };
  return (
    <div className={cn('rounded-lg border p-3 text-sm', tones[tone], className)}>{children}</div>
  );
}
