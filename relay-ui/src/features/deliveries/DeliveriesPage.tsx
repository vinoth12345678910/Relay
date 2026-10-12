import { usePageTitle } from '@/lib/usePageTitle';
import { useSearchParams } from 'react-router';
import { Button } from '@/components/ui/Button';
import { EmptyState, ErrorState, ListSkeleton } from '@/components/ui/Feedback';
import { DeliveryDrawer } from './DeliveryDrawer';
import { DeliveryFilters } from './DeliveryFilters';
import { DeliveryTable } from './DeliveryTable';
import { filtersFromParams, mergePages, withDelivery, withFilter } from './filters';
import { useDeliveryList } from './queries';

export function DeliveriesPage() {
  usePageTitle('Deliveries');
  const [params, setParams] = useSearchParams();
  const filters = filtersFromParams(params);
  const list = useDeliveryList(filters);
  const rows = list.data ? mergePages(list.data.pages) : [];
  const openId = params.get('delivery');
  const filtered = Boolean(filters.status || filters.endpointId);

  return (
    <div className="flex flex-col gap-5">
      <div>
        <h1 className="text-xl font-semibold tracking-tight">Deliveries</h1>
        <p className="mt-1 text-sm text-muted">One row per event per endpoint, newest first.</p>
      </div>
      <DeliveryFilters
        filters={filters}
        onChange={(key, value) => setParams(withFilter(withDelivery(params, null), key, value))}
        onClear={() => setParams(new URLSearchParams())}
      />
      {list.isPending ? (
        <ListSkeleton label="Loading deliveries" rows={6} />
      ) : !list.data ? (
        <ErrorState error={list.error} onRetry={() => void list.refetch()} />
      ) : rows.length === 0 ? (
        <EmptyState
          title={filtered ? 'No deliveries match these filters' : 'No deliveries yet'}
          hint={
            filtered
              ? 'Try another status or endpoint, or clear the filters.'
              : 'Publish an event that matches an endpoint filter and its deliveries show up here.'
          }
        />
      ) : (
        <>
          <DeliveryTable deliveries={rows} onOpen={(id) => setParams(withDelivery(params, id))} />
          <div className="flex items-center justify-between gap-3 text-sm text-muted">
            <span role="status">
              {rows.length} shown{list.hasNextPage ? ', more available' : ', end of list'}
            </span>
            {list.hasNextPage && (
              <Button onClick={() => void list.fetchNextPage()} loading={list.isFetchingNextPage}>
                Load more
              </Button>
            )}
          </div>
          {list.isFetchNextPageError && (
            <ErrorState error={list.error} onRetry={() => void list.fetchNextPage()} />
          )}
        </>
      )}
      <DeliveryDrawer
        id={openId}
        onClose={() => setParams(withDelivery(params, null), { replace: true })}
      />
    </div>
  );
}
