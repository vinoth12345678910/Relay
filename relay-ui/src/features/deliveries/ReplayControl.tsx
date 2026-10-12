import { useState } from 'react';
import { RotateCcw } from 'lucide-react';
import { describeError, isApiError, sentence } from '@/api/errors';
import type { DeliveryStatus } from '@/api/schemas';
import { Button } from '@/components/ui/Button';
import { ConfirmDialog } from '@/components/ui/ConfirmDialog';
import { Notice } from '@/components/ui/Feedback';
import { useToast } from '@/components/ui/Toast';
import { canReplay } from './filters';
import { useReplay } from './queries';

type Outcome = { tone: 'success' | 'warning' | 'danger'; text: string } | null;

/** Replay with confirmation. Enabled only for DEAD and SUCCEEDED, like the backend allows. */
export function ReplayControl({
  deliveryId,
  status,
  size = 'md',
  showOutcome = true,
}: {
  deliveryId: string;
  status: DeliveryStatus;
  size?: 'sm' | 'md';
  showOutcome?: boolean;
}) {
  const toast = useToast();
  const replay = useReplay();
  const [confirming, setConfirming] = useState(false);
  const [outcome, setOutcome] = useState<Outcome>(null);
  const allowed = canReplay(status);

  const run = () =>
    replay.mutate(deliveryId, {
      onSuccess: (r) => {
        const paused = /PAUSED|deleted/.test(r.message);
        setOutcome({ tone: paused ? 'warning' : 'success', text: r.message });
        toast.show({ tone: 'success', title: 'Replay queued', detail: r.message });
      },
      onError: (e) => {
        const text = isApiError(e, 409)
          ? `${sentence(describeError(e).detail)} It may already have been replayed; the view has been refreshed.`
          : `${sentence(describeError(e).title)} ${sentence(describeError(e).detail)}`;
        setOutcome({ tone: 'danger', text });
        toast.show({ tone: 'error', title: 'Replay not queued', detail: text });
      },
      onSettled: () => setConfirming(false),
    });

  return (
    <div className="flex flex-col gap-2">
      <div className="flex flex-wrap items-center gap-2">
        <Button
          size={size}
          variant={status === 'DEAD' ? 'primary' : 'secondary'}
          disabled={!allowed}
          onClick={() => {
            setOutcome(null);
            setConfirming(true);
          }}
          aria-describedby={allowed ? undefined : `replay-why-${deliveryId}`}
        >
          <RotateCcw aria-hidden className="size-3.5" /> Replay
        </Button>
        {!allowed && (
          <span id={`replay-why-${deliveryId}`} className="text-xs text-muted">
            Only Dead or Succeeded deliveries can be replayed.
          </span>
        )}
      </div>
      {showOutcome && outcome && (
        <Notice tone={outcome.tone}>
          <span role="status">{outcome.text}</span>
        </Notice>
      )}
      <ConfirmDialog
        open={confirming}
        onOpenChange={setConfirming}
        title="Replay this delivery?"
        confirmLabel="Replay"
        pending={replay.isPending}
        onConfirm={run}
        description="It goes back to Pending with a fresh retry budget and is sent again. The receiver may see it twice, so it should deduplicate on Relay-Delivery-Id."
      />
    </div>
  );
}
