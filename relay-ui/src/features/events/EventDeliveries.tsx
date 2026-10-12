import type { DeliverySummary } from '@/api/schemas';
import { DeliveryStatusBadge } from '@/components/StatusBadge';
import { CopyableId } from '@/components/ui/CopyButton';
import { EmptyState } from '@/components/ui/Feedback';
import { RelativeTime } from '@/components/ui/RelativeTime';
import { useEndpointLookup } from '@/features/endpoints/useEndpointLookup';

export function EventDeliveries({ deliveries }: { deliveries: DeliverySummary[] }) {
  const endpoint = useEndpointLookup();
  if (deliveries.length === 0) {
    return (
      <EmptyState
        title="No deliveries"
        hint="No ACTIVE or PAUSED endpoint had a filter matching this type when it was published."
      />
    );
  }
  return (
    <div className="overflow-x-auto rounded-lg border border-border bg-surface">
      <table className="w-full min-w-[640px] text-left text-sm">
        <caption className="sr-only">Deliveries of this event</caption>
        <thead className="border-b border-border text-xs text-muted">
          <tr>
            <th scope="col" className="px-4 py-2.5 font-medium">
              Delivery
            </th>
            <th scope="col" className="px-4 py-2.5 font-medium">
              Endpoint
            </th>
            <th scope="col" className="px-4 py-2.5 font-medium">
              Status
            </th>
            <th scope="col" className="px-4 py-2.5 font-medium">
              Attempts
            </th>
            <th scope="col" className="px-4 py-2.5 font-medium">
              Last code
            </th>
            <th scope="col" className="px-4 py-2.5 font-medium">
              Next attempt
            </th>
          </tr>
        </thead>
        <tbody>
          {deliveries.map((d) => (
            <tr key={d.id} className="border-b border-border last:border-0">
              <td className="px-4 py-2.5">
                <CopyableId value={d.id} label="delivery id" short />
              </td>
              <td
                className="max-w-[240px] truncate px-4 py-2.5 font-mono text-xs"
                title={endpoint(d.endpointId)?.url}
              >
                {endpoint(d.endpointId)?.url ?? (
                  <span className="text-muted">deleted endpoint</span>
                )}
              </td>
              <td className="px-4 py-2.5">
                <DeliveryStatusBadge status={d.status} />
              </td>
              <td className="px-4 py-2.5 tabular-nums">{d.attemptCount ?? '–'}</td>
              <td className="px-4 py-2.5 tabular-nums">{d.lastStatusCode ?? '–'}</td>
              <td className="px-4 py-2.5 text-muted">
                {d.nextAttemptAt && (d.status === 'PENDING' || d.status === 'IN_FLIGHT') ? (
                  <RelativeTime iso={d.nextAttemptAt} />
                ) : (
                  '–'
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
