import { Pause, Play, RotateCw } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { useNow } from '@/lib/clock';
import { formatRelative } from '@/lib/time';

export function RefreshBar({
  lastUpdated,
  paused,
  visible,
  fetching,
  onToggle,
  onRefresh,
  intervalMs,
}: {
  lastUpdated: number | null;
  paused: boolean;
  visible: boolean;
  fetching: boolean;
  onToggle: () => void;
  onRefresh: () => void;
  intervalMs: number;
}) {
  const now = useNow(1_000);
  const state = paused
    ? 'Auto-refresh paused'
    : !visible
      ? 'Auto-refresh waits while this tab is hidden'
      : `Auto-refresh every ${intervalMs / 1000} s`;
  return (
    <div className="flex flex-wrap items-center gap-2 text-xs text-muted">
      <span role="status" aria-live="off" data-testid="last-updated">
        {lastUpdated === null
          ? 'Loading…'
          : `Updated ${formatRelative(new Date(lastUpdated).toISOString(), now)}`}
      </span>
      <span aria-hidden>·</span>
      <span>{state}</span>
      <Button size="sm" variant="ghost" onClick={onToggle}>
        {paused ? (
          <Play aria-hidden className="size-3.5" />
        ) : (
          <Pause aria-hidden className="size-3.5" />
        )}
        {paused ? 'Resume auto-refresh' : 'Pause auto-refresh'}
      </Button>
      <Button
        size="sm"
        variant="ghost"
        onClick={onRefresh}
        loading={fetching}
        aria-label="Refresh now"
      >
        {!fetching && <RotateCw aria-hidden className="size-3.5" />} Refresh
      </Button>
    </div>
  );
}
