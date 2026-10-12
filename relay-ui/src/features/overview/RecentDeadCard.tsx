import { Link } from 'react-router';
import type { DeliveryPage } from '@/api/schemas';
import { RelativeTime } from '@/components/ui/RelativeTime';
import { ReplayControl } from '@/features/deliveries/ReplayControl';
import { EndpointLabel } from '@/features/endpoints/EndpointLabel';
import { recentDead } from './derive';

export function RecentDeadCard({ dead }: { dead: DeliveryPage }) {
  const rows = recentDead(dead);
  return (
    <section
      aria-labelledby="dead-title"
      className="flex flex-col gap-3 rounded-xl border border-border bg-surface p-4"
    >
      <div className="flex items-center justify-between gap-2">
        <h2 id="dead-title" className="text-sm font-semibold">
          Recent dead deliveries
        </h2>
        <Link
          to="/deliveries?status=DEAD"
          className="text-xs text-accent underline underline-offset-2"
        >
          View all
        </Link>
      </div>
      {rows.length === 0 ? (
        <p className="text-sm text-muted">
          No dead deliveries. Deliveries go dead only after every retry failed.
        </p>
      ) : (
        <ul className="flex flex-col divide-y divide-border">
          {rows.map((d) => (
            <li key={d.id} className="flex flex-wrap items-center justify-between gap-2 py-2.5">
              <div className="min-w-0 flex-1 text-sm">
                <Link
                  to={`/deliveries?status=DEAD&delivery=${d.id}`}
                  className="block truncate font-mono text-[13px] hover:underline"
                  aria-label={`Open dead delivery ${d.id.slice(0, 8)}`}
                >
                  <EndpointLabel id={d.endpointId} />
                </Link>
                <p className="text-xs text-muted">
                  {d.attemptCount} attempts · last code {d.lastStatusCode ?? 'none'} ·{' '}
                  <RelativeTime iso={d.updatedAt} />
                </p>
              </div>
              <ReplayControl deliveryId={d.id} status={d.status} size="sm" showOutcome={false} />
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
