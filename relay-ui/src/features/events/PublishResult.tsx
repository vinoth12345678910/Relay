import { Link } from 'react-router';
import { CircleCheck, History, TriangleAlert } from 'lucide-react';
import type { PublishEventResponse } from '@/api/schemas';
import { CopyableId } from '@/components/ui/CopyButton';
import { Notice } from '@/components/ui/Feedback';
import { RelativeTime } from '@/components/ui/RelativeTime';

export function PublishResult({
  result,
  submittedType,
}: {
  result: PublishEventResponse;
  submittedType: string;
}) {
  const typeIgnored = result.replayed && result.type !== submittedType;
  return (
    <section
      aria-labelledby="publish-result"
      className="flex flex-col gap-3 rounded-xl border border-border bg-surface p-4"
    >
      <h2 id="publish-result" className="flex items-center gap-2 font-semibold">
        <CircleCheck aria-hidden className="size-4 text-success" />
        {result.replayed ? 'Accepted (replayed)' : 'Accepted'}
        <span className="font-normal text-muted">· 202</span>
      </h2>
      {result.replayed && (
        <Notice tone="info">
          <p role="status" className="flex items-start gap-2">
            <History aria-hidden className="mt-0.5 size-4 shrink-0 text-info" />
            <span>
              <strong>Idempotent replay.</strong> This key was used before with the same payload, so
              Relay returned the original event (
              <code className="font-mono">Idempotent-Replayed: true</code>). No new deliveries were
              created.
            </span>
          </p>
        </Notice>
      )}
      {typeIgnored && (
        <Notice tone="warning">
          <p className="flex items-start gap-2">
            <TriangleAlert aria-hidden className="mt-0.5 size-4 shrink-0 text-warning" />
            <span>
              The original event has type <code className="font-mono">{result.type}</code>, not{' '}
              <code className="font-mono">{submittedType}</code>. Relay compares only the payload
              for idempotency, so the type you sent was ignored.
            </span>
          </p>
        </Notice>
      )}
      <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 text-sm">
        <dt className="text-muted">Event id</dt>
        <dd className="min-w-0">
          <CopyableId value={result.id} label="event id" />
        </dd>
        <dt className="text-muted">Type</dt>
        <dd className="font-mono">{result.type}</dd>
        <dt className="text-muted">Deliveries</dt>
        <dd data-testid="delivery-count">
          {result.deliveryCount}
          {result.deliveryCount === 0 && (
            <span className="ml-2 text-xs text-muted">
              No endpoint&apos;s filter matches this type.
            </span>
          )}
        </dd>
        <dt className="text-muted">Created</dt>
        <dd>
          <RelativeTime iso={result.createdAt} />
        </dd>
      </dl>
      <Link
        to={`/events/${result.id}`}
        className="text-sm font-medium text-accent underline underline-offset-2"
      >
        View event and its deliveries
      </Link>
    </section>
  );
}
