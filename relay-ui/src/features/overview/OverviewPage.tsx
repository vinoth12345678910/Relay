import { usePageTitle } from '@/lib/usePageTitle';
import { Link } from 'react-router';
import { EmptyState, ErrorState, ListSkeleton } from '@/components/ui/Feedback';
import { PausedEndpointsCard } from './PausedEndpointsCard';
import { QueueHealthCard } from './QueueHealthCard';
import { RecentDeadCard } from './RecentDeadCard';
import { RefreshBar } from './RefreshBar';
import { StatusTiles } from './StatusTiles';
import { useOverviewData } from './queries';
import { useAutoRefresh } from './useAutoRefresh';

export const OVERVIEW_REFRESH_MS = 10_000;

export function OverviewPage() {
  usePageTitle('Overview');
  const refresh = useAutoRefresh(OVERVIEW_REFRESH_MS);
  const o = useOverviewData(refresh.refetchInterval);
  const { PENDING, IN_FLIGHT, DEAD } = o.byStatus;
  const empty =
    o.endpoints.data?.length === 0 &&
    Object.values(o.byStatus).every((q) => q.data && q.data.items.length === 0);

  return (
    <div className="flex flex-col gap-5">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold tracking-tight">Overview</h1>
          <p className="mt-1 text-sm text-muted">Delivery state for the connected app.</p>
        </div>
        <RefreshBar
          lastUpdated={o.lastUpdated}
          paused={refresh.paused}
          visible={refresh.visible}
          fetching={o.isFetching}
          onToggle={refresh.toggle}
          onRefresh={o.refetchAll}
          intervalMs={OVERVIEW_REFRESH_MS}
        />
      </div>
      {empty ? (
        <EmptyState
          title="Nothing here yet"
          hint="Add an endpoint, then publish an event that matches its filter. Deliveries and their health appear here."
          action={
            <Link
              to="/endpoints"
              className="inline-flex h-9 items-center rounded-md bg-accent px-3.5 text-sm font-medium text-accent-fg hover:bg-accent-hover"
            >
              Add an endpoint
            </Link>
          }
        />
      ) : (
        <>
          <StatusTiles byStatus={o.byStatus} />
          <div className="grid gap-5 lg:grid-cols-2">
            {PENDING.data && IN_FLIGHT.data && o.endpoints.data ? (
              <QueueHealthCard
                pending={PENDING.data}
                inFlight={IN_FLIGHT.data}
                endpoints={o.endpoints.data}
              />
            ) : PENDING.isError || IN_FLIGHT.isError || o.endpoints.isError ? (
              <ErrorState
                error={PENDING.error ?? IN_FLIGHT.error ?? o.endpoints.error}
                onRetry={o.refetchAll}
              />
            ) : (
              <ListSkeleton rows={3} label="Loading queue health" />
            )}
            {o.endpoints.data ? (
              <PausedEndpointsCard endpoints={o.endpoints.data} />
            ) : o.endpoints.isError ? (
              <ErrorState error={o.endpoints.error} onRetry={() => void o.endpoints.refetch()} />
            ) : (
              <ListSkeleton rows={2} label="Loading endpoints" />
            )}
          </div>
          {DEAD.data ? (
            <RecentDeadCard dead={DEAD.data} />
          ) : DEAD.isError ? (
            <ErrorState error={DEAD.error} onRetry={() => void DEAD.refetch()} />
          ) : (
            <ListSkeleton rows={3} label="Loading dead deliveries" />
          )}
        </>
      )}
    </div>
  );
}
