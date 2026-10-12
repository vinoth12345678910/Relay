import type { ReactNode } from 'react';
import { Link } from 'react-router';
import type { Delivery } from '@/api/schemas';
import { DeliveryStatusBadge } from '@/components/StatusBadge';
import { CopyableId } from '@/components/ui/CopyButton';
import { Drawer } from '@/components/ui/Drawer';
import { EmptyState, ErrorState, ListSkeleton } from '@/components/ui/Feedback';
import { RelativeTime } from '@/components/ui/RelativeTime';
import { EndpointLabel } from '@/features/endpoints/EndpointLabel';
import { AttemptTimeline } from './AttemptTimeline';
import { ReplayControl } from './ReplayControl';
import { useAttempts, useDelivery } from './queries';

function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <>
      <dt className="text-muted">{label}</dt>
      <dd className="min-w-0">{children}</dd>
    </>
  );
}

function Summary({ d }: { d: Delivery }) {
  const due = d.status === 'PENDING' || d.status === 'IN_FLIGHT';
  return (
    <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 text-sm">
      <Row label="Status">
        <DeliveryStatusBadge status={d.status} />
      </Row>
      <Row label="Delivery id">
        <CopyableId value={d.id} label="delivery id" />
      </Row>
      <Row label="Event">
        <span className="flex items-center gap-1">
          <CopyableId value={d.eventId} label="event id" short />
          <Link
            to={`/events/${d.eventId}`}
            className="text-xs text-accent underline underline-offset-2"
          >
            open
          </Link>
        </span>
      </Row>
      <Row label="Endpoint">
        <span className="block truncate font-mono text-xs">
          <EndpointLabel id={d.endpointId} />
        </span>
        <CopyableId value={d.endpointId} label="endpoint id" short />
      </Row>
      <Row label="Attempts">
        <span className="tabular-nums">{d.attemptCount}</span>
        <span className="ml-2 text-xs text-muted">
          {d.attemptBase > 0
            ? `${d.attemptCount - d.attemptBase} since the last replay (budget restarted at ${d.attemptBase})`
            : 'attemptBase 0: never replayed'}
        </span>
      </Row>
      <Row label="Last status code">
        <span className="tabular-nums">{d.lastStatusCode ?? '–'}</span>
      </Row>
      <Row label="Next attempt">
        {due ? <RelativeTime iso={d.nextAttemptAt} /> : <span className="text-muted">–</span>}
      </Row>
      <Row label="Created">
        <RelativeTime iso={d.createdAt} />
      </Row>
      <Row label="Updated">
        <RelativeTime iso={d.updatedAt} />
      </Row>
    </dl>
  );
}

export function DeliveryDrawer({ id, onClose }: { id: string | null; onClose: () => void }) {
  const delivery = useDelivery(id);
  const attempts = useAttempts(id);
  return (
    <Drawer
      open={id !== null}
      onOpenChange={(open) => !open && onClose()}
      title={
        <span>
          Delivery <span className="font-mono text-sm text-muted">{id?.slice(0, 8)}</span>
        </span>
      }
    >
      {delivery.isPending ? (
        <ListSkeleton rows={3} label="Loading delivery" />
      ) : delivery.isError ? (
        <ErrorState error={delivery.error} onRetry={() => void delivery.refetch()} />
      ) : (
        <div className="flex flex-col gap-6">
          <Summary d={delivery.data} />
          <ReplayControl
            key={delivery.data.id}
            deliveryId={delivery.data.id}
            status={delivery.data.status}
          />
          <section aria-labelledby="attempts-title" className="flex flex-col gap-3">
            <h3 id="attempts-title" className="text-sm font-semibold">
              Attempts
            </h3>
            {attempts.isPending ? (
              <ListSkeleton rows={2} label="Loading attempts" />
            ) : attempts.isError ? (
              <ErrorState error={attempts.error} onRetry={() => void attempts.refetch()} />
            ) : attempts.data.length === 0 ? (
              <EmptyState
                title="No attempts yet"
                hint="A worker has not tried this delivery yet. Pending deliveries are picked up within seconds unless the endpoint is paused."
              />
            ) : (
              <AttemptTimeline attempts={attempts.data} />
            )}
          </section>
        </div>
      )}
    </Drawer>
  );
}
