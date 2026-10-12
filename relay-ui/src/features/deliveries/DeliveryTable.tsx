import { Link } from 'react-router';
import { PanelRightOpen } from 'lucide-react';
import type { Delivery } from '@/api/schemas';
import { DeliveryStatusBadge } from '@/components/StatusBadge';
import { CopyableId } from '@/components/ui/CopyButton';
import { RelativeTime } from '@/components/ui/RelativeTime';
import { EndpointLabel } from '@/features/endpoints/EndpointLabel';

export function DeliveryTable({
  deliveries,
  onOpen,
}: {
  deliveries: Delivery[];
  onOpen: (id: string) => void;
}) {
  return (
    <div className="relative overflow-x-auto rounded-lg border border-border bg-surface">
      <table className="w-full min-w-[860px] text-left text-sm">
        <caption className="sr-only">Deliveries, newest first</caption>
        <thead className="border-b border-border text-xs text-muted">
          <tr>
            <th scope="col" className="px-4 py-2.5 font-medium">
              Delivery
            </th>
            <th scope="col" className="px-4 py-2.5 font-medium">
              Status
            </th>
            <th scope="col" className="px-4 py-2.5 font-medium">
              Endpoint
            </th>
            <th scope="col" className="px-4 py-2.5 font-medium">
              Event
            </th>
            <th scope="col" className="px-4 py-2.5 font-medium">
              Attempts
            </th>
            <th scope="col" className="px-4 py-2.5 font-medium">
              Last code
            </th>
            <th scope="col" className="px-4 py-2.5 font-medium">
              Created
            </th>
            <th scope="col" className="px-4 py-2.5">
              <span className="sr-only">Details</span>
            </th>
          </tr>
        </thead>
        <tbody>
          {deliveries.map((d) => (
            <tr key={d.id} className="border-b border-border last:border-0 hover:bg-surface-2/60">
              <td className="px-4 py-2.5">
                <CopyableId value={d.id} label="delivery id" short />
              </td>
              <td className="px-4 py-2.5">
                <DeliveryStatusBadge status={d.status} />
              </td>
              <td className="max-w-[220px] truncate px-4 py-2.5 font-mono text-xs">
                <EndpointLabel id={d.endpointId} />
              </td>
              <td className="px-4 py-2.5">
                <Link
                  to={`/events/${d.eventId}`}
                  className="font-mono text-xs hover:underline"
                  aria-label={`Event ${d.eventId}`}
                >
                  {d.eventId.slice(0, 8)}…
                </Link>
              </td>
              <td className="px-4 py-2.5 tabular-nums">{d.attemptCount}</td>
              <td className="px-4 py-2.5 tabular-nums">{d.lastStatusCode ?? '–'}</td>
              <td className="px-4 py-2.5 text-muted">
                <RelativeTime iso={d.createdAt} />
              </td>
              <td className="px-4 py-2.5 text-right">
                <button
                  type="button"
                  onClick={() => onOpen(d.id)}
                  className="inline-flex items-center gap-1 rounded-md px-2 py-1 text-xs font-medium hover:bg-surface-2"
                  aria-label={`Open details for delivery ${d.id.slice(0, 8)}`}
                >
                  <PanelRightOpen aria-hidden className="size-3.5" /> Details
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
