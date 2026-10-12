import { usePageTitle } from '@/lib/usePageTitle';
import { Link, useParams } from 'react-router';
import { ArrowLeft } from 'lucide-react';
import { CodeBlock } from '@/components/ui/CodeBlock';
import { CopyableId, CopyButton } from '@/components/ui/CopyButton';
import { ErrorState, ListSkeleton, Skeleton } from '@/components/ui/Feedback';
import { RelativeTime } from '@/components/ui/RelativeTime';
import { isId, prettyJson } from '@/lib/json';
import { EventDeliveries } from './EventDeliveries';
import { useEvent } from './queries';

export function EventDetailPage() {
  const { id = '' } = useParams();
  const event = useEvent(id);
  usePageTitle(event.data ? `Event ${event.data.type}` : 'Event');

  return (
    <div className="flex flex-col gap-5">
      <Link
        to="/events"
        className="inline-flex w-fit items-center gap-1 text-sm text-muted hover:text-fg"
      >
        <ArrowLeft aria-hidden className="size-4" /> Events
      </Link>
      {!isId(id) ? (
        <ErrorState
          error={null}
          title="Not a valid event id"
          detail="Event ids are UUIDs, like 4f1c2a3b-0000-4000-8000-000000000000."
        />
      ) : event.isPending ? (
        <div className="flex flex-col gap-3" role="status" aria-label="Loading event">
          <Skeleton className="h-7 w-64" />
          <Skeleton className="h-40 w-full" />
        </div>
      ) : event.isError ? (
        <ErrorState error={event.error} onRetry={() => void event.refetch()} />
      ) : (
        <>
          <div className="flex flex-col gap-1">
            <h1 className="font-mono text-xl font-semibold tracking-tight break-all">
              {event.data.type}
            </h1>
            <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-muted">
              <CopyableId value={event.data.id} label="event id" />
              <span>
                Published <RelativeTime iso={event.data.createdAt} />
              </span>
            </div>
          </div>
          <section aria-labelledby="payload-title" className="flex flex-col gap-2">
            <div className="flex items-center justify-between">
              <h2 id="payload-title" className="text-sm font-semibold">
                Payload
              </h2>
              <CopyButton value={prettyJson(event.data.payload)} label="payload" />
            </div>
            <CodeBlock text={prettyJson(event.data.payload)} label="Event payload (read-only)" />
            <p className="text-xs text-muted">
              Formatted for reading. Receivers get (and signatures cover) the exact bytes Relay
              stored.
            </p>
          </section>
          <section aria-labelledby="deliveries-title" className="flex flex-col gap-2">
            <h2 id="deliveries-title" className="text-sm font-semibold">
              Deliveries{' '}
              <span className="font-normal text-muted">({event.data.deliveries.length})</span>
            </h2>
            {event.isRefetching && <ListSkeleton rows={1} label="Refreshing deliveries" />}
            <EventDeliveries deliveries={event.data.deliveries} />
          </section>
        </>
      )}
    </div>
  );
}
