import { Skeleton } from '@/components/ui/Feedback';
import { useEndpoints } from './queries';

/**
 * The URL of an endpoint by id. Distinguishes "still loading" and "list failed" from "not in the
 * list", which is the only case that really means deleted (the API hides DISABLED endpoints).
 */
export function EndpointLabel({ id, className }: { id: string; className?: string }) {
  const { data, isPending } = useEndpoints();
  if (isPending) return <Skeleton className="inline-block h-3.5 w-40 align-middle" />;
  if (!data) return <span className="text-muted">unknown endpoint ({id.slice(0, 8)}…)</span>;
  const e = data.find((x) => x.id === id);
  if (!e) return <span className="text-muted">deleted endpoint</span>;
  return (
    <span className={className} title={e.url}>
      {e.url}
    </span>
  );
}
