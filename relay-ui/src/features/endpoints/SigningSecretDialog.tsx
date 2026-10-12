import { ExternalLink, TriangleAlert } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { CopyButton } from '@/components/ui/CopyButton';
import { Dialog } from '@/components/ui/Dialog';

export const VERIFY_DOCS_URL =
  'https://github.com/vinoth12345678910/Relay/blob/main/docs/verifying-signatures.md';

export type OneTimeSecret = { endpointUrl: string; signingSecret: string };

/**
 * Shows an endpoint's signing secret exactly once. The secret exists only in the parent's state
 * while this is open; closing calls onClose, which must drop it.
 */
export function SigningSecretDialog({
  secret,
  onClose,
}: {
  secret: OneTimeSecret | null;
  onClose: () => void;
}) {
  return (
    <Dialog
      open={secret !== null}
      onOpenChange={(open) => !open && onClose()}
      dismissible={false}
      title="Endpoint created: copy the signing secret"
      description={secret ? `For ${secret.endpointUrl}` : undefined}
      footer={
        <Button variant="primary" onClick={onClose}>
          I have stored the secret
        </Button>
      }
    >
      {secret && (
        <div className="flex flex-col gap-4">
          <div className="flex items-center gap-2 rounded-md border border-border bg-code-bg p-2">
            <code
              className="min-w-0 flex-1 font-mono text-[12.5px] break-all"
              data-testid="one-time-secret"
            >
              {secret.signingSecret}
            </code>
            <CopyButton value={secret.signingSecret} label="signing secret" />
          </div>
          <p className="flex items-start gap-2 rounded-md border border-warning/40 bg-warning-bg p-3 text-sm text-warning">
            <TriangleAlert aria-hidden className="mt-0.5 size-4 shrink-0" />
            You will not see this secret again. Relay stores it encrypted and never returns it. If
            you lose it, delete the endpoint and add it again.
          </p>
          <div className="text-sm text-muted">
            <p className="font-medium text-fg">Verifying signatures in your receiver</p>
            <p className="mt-1">
              The HMAC key is <strong>not</strong> the <code className="font-mono">whsec_…</code>{' '}
              string itself: strip the <code className="font-mono">whsec_</code> prefix and{' '}
              <strong>base64url-decode</strong> the rest, then compute{' '}
              <code className="font-mono">
                HMAC-SHA256(key, "&lt;Relay-Timestamp&gt;.&lt;raw body&gt;")
              </code>{' '}
              and compare it to the <code className="font-mono">v1=</code> value in{' '}
              <code className="font-mono">Relay-Signature</code>.
            </p>
            <a
              href={VERIFY_DOCS_URL}
              target="_blank"
              rel="noopener noreferrer"
              className="mt-2 inline-flex items-center gap-1 text-accent underline underline-offset-2"
            >
              docs/verifying-signatures.md (Python, Node, Java){' '}
              <ExternalLink aria-hidden className="size-3.5" />
            </a>
          </div>
        </div>
      )}
    </Dialog>
  );
}
