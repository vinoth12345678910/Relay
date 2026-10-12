import { useNow } from '@/lib/clock';
import { formatAbsolute, formatRelative } from '@/lib/time';
import { Tooltip } from './Tooltip';

/**
 * "5 minutes ago" with the absolute time on hover. Not a tab stop (a 50-row table would add 50);
 * screen readers get the absolute time in the accessible name instead.
 */
export function RelativeTime({ iso }: { iso: string }) {
  const now = useNow();
  const absolute = formatAbsolute(iso);
  const relative = formatRelative(iso, now);
  return (
    <Tooltip content={absolute}>
      <time
        dateTime={iso}
        aria-label={`${relative} (${absolute})`}
        className="cursor-default whitespace-nowrap underline decoration-muted/60 decoration-dotted underline-offset-2"
      >
        {relative}
      </time>
    </Tooltip>
  );
}
