import { CirclePause, Play } from 'lucide-react';
import { describeError } from '@/api/errors';
import type { Endpoint } from '@/api/schemas';
import { Button } from '@/components/ui/Button';
import { useToast } from '@/components/ui/Toast';
import { useResumeEndpoint } from '@/features/endpoints/queries';

export function PausedEndpointsCard({ endpoints }: { endpoints: Endpoint[] }) {
  const paused = endpoints.filter((e) => e.status === 'PAUSED');
  const resume = useResumeEndpoint();
  const toast = useToast();
  return (
    <section
      aria-labelledby="paused-title"
      className="flex flex-col gap-3 rounded-xl border border-border bg-surface p-4"
    >
      <h2 id="paused-title" className="text-sm font-semibold">
        Paused endpoints <span className="font-normal text-muted">({paused.length})</span>
      </h2>
      {paused.length === 0 ? (
        <p className="text-sm text-muted">
          All endpoints are delivering. The circuit breaker pauses an endpoint after repeated
          failures; it would show up here.
        </p>
      ) : (
        <ul className="flex flex-col divide-y divide-border">
          {paused.map((e) => (
            <li key={e.id} className="flex flex-wrap items-start justify-between gap-2 py-2.5">
              <div className="min-w-0 flex-1">
                <p
                  className="flex items-center gap-1.5 truncate font-mono text-[13px]"
                  title={e.url}
                >
                  <CirclePause aria-hidden className="size-4 shrink-0 text-warning" />
                  <span className="sr-only">Paused: </span>
                  {e.url}
                </p>
                <p className="mt-0.5 text-xs text-muted">
                  {e.pausedReason ?? 'No reason given'} · {e.consecutiveFailures} consecutive
                  failures
                </p>
              </div>
              <Button
                size="sm"
                loading={resume.isPending && resume.variables === e.id}
                aria-label={`Resume ${e.description || e.url}`}
                onClick={() =>
                  resume.mutate(e.id, {
                    onSuccess: () =>
                      toast.show({
                        tone: 'success',
                        title: 'Endpoint resumed',
                        detail: 'Its queued deliveries will be sent now.',
                      }),
                    onError: (err) =>
                      toast.show({
                        tone: 'error',
                        title: 'Could not resume',
                        detail: describeError(err).detail,
                      }),
                  })
                }
              >
                <Play aria-hidden className="size-3.5" /> Resume
              </Button>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
