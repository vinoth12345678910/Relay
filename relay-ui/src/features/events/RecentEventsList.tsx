import { Link } from 'react-router';
import { RelativeTime } from '@/components/ui/RelativeTime';
import { useRecentEvents } from './recentEvents';

export function RecentEventsList() {
  const items = useRecentEvents();
  return (
    <section
      aria-labelledby="recent-title"
      className="rounded-xl border border-border bg-surface p-4"
    >
      <h2 id="recent-title" className="text-sm font-semibold">
        Published from this tab
      </h2>
      <p className="mt-1 text-xs text-muted">
        Relay has no “list events” API, so only events sent from here appear. Cleared on disconnect.
      </p>
      {items.length === 0 ? (
        <p className="mt-3 text-sm text-muted">Nothing yet. Publish an event, or open one by id.</p>
      ) : (
        <ul className="mt-3 flex flex-col divide-y divide-border">
          {items.map((e) => (
            <li key={e.id} className="flex items-center justify-between gap-2 py-2 text-sm">
              <Link
                to={`/events/${e.id}`}
                className="min-w-0 truncate font-mono text-[13px] hover:underline"
              >
                {e.type}
              </Link>
              <span className="shrink-0 text-xs text-muted">
                {e.deliveryCount} deliv. · <RelativeTime iso={e.createdAt} />
              </span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
