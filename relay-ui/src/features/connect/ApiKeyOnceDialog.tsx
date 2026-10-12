import { TriangleAlert } from 'lucide-react';
import type { CreateAppResponse } from '@/api/schemas';
import { Button } from '@/components/ui/Button';
import { CopyButton } from '@/components/ui/CopyButton';
import { Dialog } from '@/components/ui/Dialog';

/** Shows a new app's API key exactly once. Closing the dialog drops it from memory. */
export function ApiKeyOnceDialog({
  app,
  onClose,
  onConnect,
}: {
  app: CreateAppResponse | null;
  onClose: () => void;
  onConnect: (apiKey: string) => void;
}) {
  return (
    <Dialog
      open={app !== null}
      onOpenChange={(open) => !open && onClose()}
      dismissible={false}
      title={app ? `App "${app.name}" created` : 'App created'}
      description="Copy the API key now. Relay stores only a hash, so it cannot show it again."
      footer={
        <>
          <Button onClick={onClose}>I saved it, close</Button>
          {app && (
            <Button variant="primary" onClick={() => onConnect(app.apiKey)}>
              Connect with this key
            </Button>
          )}
        </>
      }
    >
      {app && (
        <div className="flex flex-col gap-3">
          <div className="flex items-center gap-2 rounded-md border border-border bg-code-bg p-2">
            <code
              className="min-w-0 flex-1 font-mono text-[12.5px] break-all"
              data-testid="one-time-api-key"
            >
              {app.apiKey}
            </code>
            <CopyButton value={app.apiKey} label="API key" />
          </div>
          <p className="flex items-start gap-2 text-sm text-warning">
            <TriangleAlert aria-hidden className="mt-0.5 size-4 shrink-0" />
            You will not see this key again. Lost it? Create a new app.
          </p>
        </div>
      )}
    </Dialog>
  );
}
