import { usePageTitle } from '@/lib/usePageTitle';
import { Plus } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { ConfirmDialog } from '@/components/ui/ConfirmDialog';
import { EmptyState, ErrorState, ListSkeleton } from '@/components/ui/Feedback';
import { EndpointActions } from './EndpointActions';
import { EndpointFormDialog } from './EndpointFormDialog';
import { EndpointTable } from './EndpointTable';
import { PauseDialog } from './PauseDialog';
import { SigningSecretDialog } from './SigningSecretDialog';
import { useEndpoints } from './queries';
import { useEndpointManager } from './useEndpointManager';

export function EndpointsPage() {
  usePageTitle('Endpoints');
  const endpoints = useEndpoints();
  const m = useEndpointManager();
  const addButton = (
    <Button variant="primary" onClick={m.openCreate}>
      <Plus aria-hidden className="size-4" /> Add endpoint
    </Button>
  );

  return (
    <div className="flex flex-col gap-5">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold tracking-tight">Endpoints</h1>
          <p className="mt-1 text-sm text-muted">
            Where Relay delivers your events. Deleted endpoints are hidden.
          </p>
        </div>
        {addButton}
      </div>
      {endpoints.isPending ? (
        <ListSkeleton label="Loading endpoints" />
      ) : endpoints.isError ? (
        <ErrorState error={endpoints.error} onRetry={() => void endpoints.refetch()} />
      ) : endpoints.data.length === 0 ? (
        <EmptyState
          title="No endpoints yet"
          hint="Add an endpoint to start receiving webhooks. Events published before that are not delivered to it."
          action={addButton}
        />
      ) : (
        <EndpointTable
          endpoints={endpoints.data}
          actions={(e) => (
            <EndpointActions endpoint={e} onAction={m.onAction} resuming={m.resumingId === e.id} />
          )}
        />
      )}

      <EndpointFormDialog
        open={m.formTarget !== null}
        endpoint={m.formTarget?.mode === 'edit' ? m.formTarget.endpoint : null}
        onOpenChange={(open) => !open && m.closeForm()}
        onSubmit={m.submitForm}
      />
      <PauseDialog
        endpoint={m.pauseTarget}
        onOpenChange={(open) => !open && m.closePause()}
        onSubmit={m.submitPause}
      />
      <ConfirmDialog
        open={m.deleteTarget !== null}
        onOpenChange={(open) => !open && m.closeDelete()}
        title="Delete endpoint?"
        tone="danger"
        confirmLabel="Delete endpoint"
        pending={m.deleting}
        onConfirm={m.confirmDelete}
        description="Relay stops delivering to it. Deliveries already queued for it will not be sent. This cannot be undone from the UI."
      >
        <p className="font-mono text-xs break-all text-muted">{m.deleteTarget?.url}</p>
      </ConfirmDialog>
      <SigningSecretDialog secret={m.secret} onClose={m.closeSecret} />
    </div>
  );
}
