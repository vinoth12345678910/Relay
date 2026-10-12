import type { ReactNode } from 'react';
import type { Endpoint } from '@/api/schemas';
import { EndpointStatusBadge } from '@/components/StatusBadge';
import { CopyableId } from '@/components/ui/CopyButton';
import { RelativeTime } from '@/components/ui/RelativeTime';

export function EndpointTable({
  endpoints,
  actions,
}: {
  endpoints: Endpoint[];
  actions?: (e: Endpoint) => ReactNode;
}) {
  return (
    <div className="overflow-x-auto rounded-lg border border-border bg-surface">
      <table className="w-full min-w-[720px] text-left text-sm">
        <caption className="sr-only">Endpoints</caption>
        <thead className="border-b border-border text-xs text-muted">
          <tr>
            <th scope="col" className="px-4 py-2.5 font-medium">
              Endpoint
            </th>
            <th scope="col" className="px-4 py-2.5 font-medium">
              Filter
            </th>
            <th scope="col" className="px-4 py-2.5 font-medium">
              Status
            </th>
            <th scope="col" className="px-4 py-2.5 font-medium">
              Failures
            </th>
            <th scope="col" className="px-4 py-2.5 font-medium">
              Created
            </th>
            {actions && (
              <th scope="col" className="px-4 py-2.5 text-right font-medium">
                Actions
              </th>
            )}
          </tr>
        </thead>
        <tbody>
          {endpoints.map((e) => (
            <tr key={e.id} className="border-b border-border align-top last:border-0">
              <td className="max-w-[320px] px-4 py-3">
                <p className="truncate font-mono text-[13px]" title={e.url}>
                  {e.url}
                </p>
                {e.description && (
                  <p className="mt-0.5 truncate text-xs text-muted">{e.description}</p>
                )}
                <div className="mt-1 text-xs text-muted">
                  <CopyableId value={e.id} label="endpoint id" short />
                </div>
              </td>
              <td className="px-4 py-3">
                <code className="rounded bg-surface-2 px-1.5 py-0.5 font-mono text-xs">
                  {e.eventTypeFilter}
                </code>
              </td>
              <td className="px-4 py-3">
                <EndpointStatusBadge status={e.status} />
                {e.status === 'PAUSED' && e.pausedReason && (
                  <p className="mt-1 max-w-[220px] text-xs text-muted">{e.pausedReason}</p>
                )}
              </td>
              <td className="px-4 py-3 tabular-nums">{e.consecutiveFailures}</td>
              <td className="px-4 py-3 text-muted">
                <RelativeTime iso={e.createdAt} />
              </td>
              {actions && <td className="px-4 py-3 text-right">{actions(e)}</td>}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
