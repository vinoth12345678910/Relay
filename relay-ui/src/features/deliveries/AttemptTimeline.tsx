import type { Attempt } from '@/api/schemas';
import { CodeBlock } from '@/components/ui/CodeBlock';
import { RelativeTime } from '@/components/ui/RelativeTime';
import { cn } from '@/lib/cn';
import { formatDuration } from '@/lib/time';

function CodeLabel({ code }: { code: number | null }) {
  if (code === null)
    return (
      <span className="rounded border border-border px-1.5 py-0.5 text-xs text-muted">
        no response
      </span>
    );
  const ok = code >= 200 && code < 300;
  return (
    <span
      className={cn(
        'rounded border px-1.5 py-0.5 font-mono text-xs',
        ok
          ? 'border-success/40 bg-success-bg text-success'
          : 'border-danger/40 bg-danger-bg text-danger',
      )}
    >
      HTTP {code}
    </span>
  );
}

/** Every HTTP attempt, oldest first. Snippets are untrusted receiver output: shown as text only. */
export function AttemptTimeline({ attempts }: { attempts: Attempt[] }) {
  return (
    <ol className="relative flex flex-col gap-4 border-l border-border pl-5">
      {attempts.map((a) => (
        <li key={a.attemptNumber} className="relative">
          <span
            aria-hidden
            className={cn(
              'absolute top-1.5 -left-[25px] size-2.5 rounded-full border-2 border-surface',
              a.statusCode !== null && a.statusCode < 300 && a.statusCode >= 200
                ? 'bg-success'
                : 'bg-danger',
            )}
          />
          <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm">
            <span className="font-medium">Attempt {a.attemptNumber}</span>
            <CodeLabel code={a.statusCode} />
            <span className="text-xs text-muted tabular-nums">{formatDuration(a.durationMs)}</span>
            <span className="text-xs text-muted">
              <RelativeTime iso={a.startedAt} />
            </span>
          </div>
          {a.error && <p className="mt-1 text-sm text-danger">{a.error}</p>}
          {a.responseSnippet ? (
            <CodeBlock
              className="mt-2 max-h-48"
              text={a.responseSnippet}
              label={`Response snippet of attempt ${a.attemptNumber}`}
            />
          ) : (
            <p className="mt-1 text-xs text-muted">No response body recorded.</p>
          )}
        </li>
      ))}
    </ol>
  );
}
