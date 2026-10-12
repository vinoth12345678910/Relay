import { Link } from 'react-router';
import { Pause, Pencil, Play, Trash2, Waypoints } from 'lucide-react';
import type { Endpoint } from '@/api/schemas';
import { Button } from '@/components/ui/Button';

export type EndpointAction = 'edit' | 'pause' | 'resume' | 'delete';

export function EndpointActions({
  endpoint,
  onAction,
  resuming,
}: {
  endpoint: Endpoint;
  onAction: (action: EndpointAction, e: Endpoint) => void;
  resuming: boolean;
}) {
  const label = endpoint.description || endpoint.url;
  return (
    <div className="flex flex-wrap justify-end gap-1">
      <Link
        to={`/deliveries?endpointId=${encodeURIComponent(endpoint.id)}`}
        className="inline-flex h-8 items-center gap-1.5 rounded-md px-2.5 text-xs font-medium hover:bg-surface-2"
        aria-label={`Deliveries for ${label}`}
      >
        <Waypoints aria-hidden className="size-3.5" />
        Deliveries
      </Link>
      <Button
        size="sm"
        variant="ghost"
        onClick={() => onAction('edit', endpoint)}
        aria-label={`Edit ${label}`}
      >
        <Pencil aria-hidden className="size-3.5" /> Edit
      </Button>
      {endpoint.status === 'PAUSED' ? (
        <Button
          size="sm"
          variant="ghost"
          loading={resuming}
          onClick={() => onAction('resume', endpoint)}
          aria-label={`Resume ${label}`}
        >
          {!resuming && <Play aria-hidden className="size-3.5" />} Resume
        </Button>
      ) : (
        <Button
          size="sm"
          variant="ghost"
          onClick={() => onAction('pause', endpoint)}
          aria-label={`Pause ${label}`}
        >
          <Pause aria-hidden className="size-3.5" /> Pause
        </Button>
      )}
      <Button
        size="sm"
        variant="ghost"
        className="text-danger"
        onClick={() => onAction('delete', endpoint)}
        aria-label={`Delete ${label}`}
      >
        <Trash2 aria-hidden className="size-3.5" /> Delete
      </Button>
    </div>
  );
}
