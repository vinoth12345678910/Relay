const rtf = new Intl.RelativeTimeFormat('en', { numeric: 'auto' });
const UNITS: Array<[Intl.RelativeTimeFormatUnit, number]> = [
  ['year', 365 * 24 * 3600],
  ['month', 30 * 24 * 3600],
  ['day', 24 * 3600],
  ['hour', 3600],
  ['minute', 60],
  ['second', 1],
];

/** "3 minutes ago" / "in 2 hours", relative to `now` (ms). */
export function formatRelative(iso: string, now: number): string {
  const diffSec = Math.round((Date.parse(iso) - now) / 1000);
  const abs = Math.abs(diffSec);
  if (abs < 5) return 'just now';
  for (const [unit, size] of UNITS) {
    if (abs >= size || unit === 'second') return rtf.format(Math.round(diffSec / size), unit);
  }
  return rtf.format(diffSec, 'second');
}

/** Absolute local time with the zone, e.g. "12 Oct 2026, 11:02:03 UTC". */
const absoluteFormat = new Intl.DateTimeFormat('en-GB', { dateStyle: 'medium', timeStyle: 'long' });

export function formatAbsolute(iso: string): string {
  return absoluteFormat.format(new Date(iso));
}

export function formatDuration(ms: number): string {
  if (ms < 1000) return `${ms} ms`;
  return `${(ms / 1000).toFixed(ms < 10_000 ? 2 : 1)} s`;
}
