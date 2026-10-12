import { Link } from 'react-router';
import type { UseQueryResult } from '@tanstack/react-query';
import { CircleCheck, CircleX, Clock, Send, type LucideIcon } from 'lucide-react';
import type { DeliveryPage, DeliveryStatus } from '@/api/schemas';
import { Skeleton } from '@/components/ui/Feedback';
import { cn } from '@/lib/cn';
import { countFromPage, formatCount, SAMPLE_SIZE } from './derive';

const TILES: Record<DeliveryStatus, { label: string; icon: LucideIcon; tone: string }> = {
  PENDING: { label: 'Pending', icon: Clock, tone: 'text-warning' },
  IN_FLIGHT: { label: 'In flight', icon: Send, tone: 'text-info' },
  SUCCEEDED: { label: 'Succeeded', icon: CircleCheck, tone: 'text-success' },
  DEAD: { label: 'Dead', icon: CircleX, tone: 'text-danger' },
};

/** KPI row: value in text ink; status identity from icon + label, never colour alone. */
export function StatusTiles({
  byStatus,
}: {
  byStatus: Record<DeliveryStatus, UseQueryResult<DeliveryPage>>;
}) {
  return (
    <section aria-labelledby="counts-title" className="flex flex-col gap-2">
      <h2 id="counts-title" className="sr-only">
        Deliveries by status
      </h2>
      <ul className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        {(Object.keys(TILES) as DeliveryStatus[]).map((status) => {
          const { label, icon: Icon, tone } = TILES[status];
          const q = byStatus[status];
          return (
            <li key={status}>
              <Link
                to={`/deliveries?status=${status}`}
                className="flex h-full flex-col gap-1 rounded-xl border border-border bg-surface p-4 hover:border-border-strong"
              >
                <span className="flex items-center gap-1.5 text-sm text-muted">
                  <Icon aria-hidden className={cn('size-4', tone)} /> {label}
                </span>
                {q.data ? (
                  <span
                    className="text-3xl font-semibold tracking-tight"
                    data-testid={`count-${status}`}
                  >
                    {formatCount(countFromPage(q.data))}
                  </span>
                ) : q.isError ? (
                  <span className="text-sm text-danger">Could not load</span>
                ) : (
                  <Skeleton className="h-9 w-16" />
                )}
              </Link>
            </li>
          );
        })}
      </ul>
      <p className="text-xs text-muted">
        Counted from the newest {SAMPLE_SIZE} deliveries per status (Relay has no stats endpoint). “
        {SAMPLE_SIZE}+” means at least that many.
      </p>
    </section>
  );
}
