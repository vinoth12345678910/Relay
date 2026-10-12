import { useNow } from '@/lib/clock';
import { formatAbsolute, formatRelative } from '@/lib/time';
import { Tooltip } from './Tooltip';

/** "5 minutes ago", with the absolute time on hover/focus and in the <time> element. */
export function RelativeTime({ iso }: { iso: string }) {
  const now = useNow();
  const absolute = formatAbsolute(iso);
  return (
    <Tooltip content={absolute}>
      <button
        type="button"
        className="cursor-default rounded-sm whitespace-nowrap underline decoration-muted/60 decoration-dotted underline-offset-2"
      >
        <time dateTime={iso} aria-label={`${formatRelative(iso, now)} (${absolute})`}>
          {formatRelative(iso, now)}
        </time>
      </button>
    </Tooltip>
  );
}
