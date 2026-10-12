import { EmptyState, ErrorState, ListSkeleton } from '@/components/ui/Feedback';
import { EndpointTable } from './EndpointTable';
import { useEndpoints } from './queries';

export function EndpointsPage() {
  const endpoints = useEndpoints();

  return (
    <div className="flex flex-col gap-5">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold tracking-tight">Endpoints</h1>
          <p className="mt-1 text-sm text-muted">
            Where Relay delivers your events. Deleted endpoints are hidden.
          </p>
        </div>
      </div>
      {endpoints.isPending ? (
        <ListSkeleton label="Loading endpoints" />
      ) : endpoints.isError ? (
        <ErrorState error={endpoints.error} onRetry={() => void endpoints.refetch()} />
      ) : endpoints.data.length === 0 ? (
        <EmptyState
          title="No endpoints yet"
          hint="Add an endpoint to start receiving webhooks. Events published before that are not delivered to it."
        />
      ) : (
        <EndpointTable endpoints={endpoints.data} />
      )}
    </div>
  );
}
