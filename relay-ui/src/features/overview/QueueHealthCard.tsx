import { Activity, CircleAlert, MoonStar } from 'lucide-react';
import type { DeliveryPage, Endpoint } from '@/api/schemas';
import { useNow } from '@/lib/clock';
import { deriveQueueHealth, formatAge, OVERDUE_GRACE_MS, SAMPLE_SIZE } from './derive';

export function QueueHealthCard({
  pending,
  inFlight,
  endpoints,
}: {
  pending: DeliveryPage;
  inFlight: DeliveryPage;
  endpoints: Endpoint[];
}) {
  const now = useNow(5_000);
  const h = deriveQueueHealth(pending, endpoints, now);
  const head =
    h.state === 'backlog'
      ? { icon: CircleAlert, tone: 'text-warning', text: 'Backlog' }
      : h.state === 'healthy'
        ? { icon: Activity, tone: 'text-success', text: 'Keeping up' }
        : { icon: MoonStar, tone: 'text-muted', text: 'Idle' };
  const Icon = head.icon;
  return (
    <section
      aria-labelledby="health-title"
      className="flex flex-col gap-3 rounded-xl border border-border bg-surface p-4"
    >
      <h2 id="health-title" className="text-sm font-semibold">
        Queue health
      </h2>
      <p className="flex items-center gap-2 text-lg font-semibold">
        <Icon aria-hidden className={`size-5 ${head.tone}`} /> {head.text}
      </p>
      <dl className="grid grid-cols-[1fr_auto] gap-x-4 gap-y-1.5 text-sm">
        <dt className="text-muted">Overdue pending (&gt;{OVERDUE_GRACE_MS / 1000}s late)</dt>
        <dd className="text-right font-medium">
          {h.overdue}
          {h.sampled && h.overdue > 0 ? '+' : ''}
        </dd>
        <dt className="text-muted">Most overdue</dt>
        <dd className="text-right font-medium">
          {h.oldestOverdueMs === null ? '–' : `${formatAge(h.oldestOverdueMs)} late`}
        </dd>
        <dt className="text-muted">Waiting on paused endpoints</dt>
        <dd className="text-right font-medium">{h.waitingOnPaused}</dd>
        {h.orphaned > 0 && (
          <>
            <dt className="text-muted">Stuck on deleted endpoints (never sent)</dt>
            <dd className="text-right font-medium">{h.orphaned}</dd>
          </>
        )}
        <dt className="text-muted">In flight now</dt>
        <dd className="text-right font-medium">
          {inFlight.items.length}
          {inFlight.nextCursor ? '+' : ''}
        </dd>
      </dl>
      <p className="text-xs text-muted">
        From the newest {SAMPLE_SIZE} pending deliveries. Deliveries to paused endpoints wait on
        purpose and are not counted as overdue.
        {h.sampled
          ? ' Older pending deliveries are not in the sample, so “most overdue” is a lower bound.'
          : ''}
      </p>
    </section>
  );
}
