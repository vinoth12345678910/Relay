import { describe, expect, it } from 'vitest';
import type { Delivery, Endpoint } from '@/api/schemas';
import { countFromPage, deriveQueueHealth, formatAge, formatCount } from './derive';

const NOW = Date.parse('2026-10-12T10:00:00Z');
const d = (endpointId: string, nextAttemptAt: string): Delivery => ({
  id: `00000000-0000-4000-8000-${Math.random().toString(16).slice(2, 14).padEnd(12, '0')}`,
  eventId: '00000000-0000-4000-8000-000000000001',
  endpointId,
  status: 'PENDING',
  attemptCount: 0,
  attemptBase: 0,
  nextAttemptAt,
  lastStatusCode: null,
  createdAt: nextAttemptAt,
  updatedAt: nextAttemptAt,
});
const ep = (id: string, status: Endpoint['status']): Endpoint => ({
  id,
  url: 'https://x',
  description: null,
  eventTypeFilter: '*',
  status,
  consecutiveFailures: 0,
  pausedReason: null,
  createdAt: '2026-10-12T09:00:00Z',
  updatedAt: '2026-10-12T09:00:00Z',
});

describe('overview derivations', () => {
  it('labels capped counts as lower bounds', () => {
    expect(formatCount(countFromPage({ items: [], nextCursor: null }))).toBe('0');
    expect(
      formatCount(
        countFromPage({ items: Array(200).fill(d('a', '2026-10-12T10:00:00Z')), nextCursor: 'c' }),
      ),
    ).toBe('200+');
  });

  it('counts overdue pending only for non-paused endpoints, with the oldest lateness', () => {
    const h = deriveQueueHealth(
      {
        items: [
          d('active', '2026-10-12T09:50:00Z'), // 10 min late
          d('active', '2026-10-12T09:59:30Z'), // 30 s: within grace
          d('paused', '2026-10-12T08:00:00Z'), // waits on purpose
          d('deleted', '2026-10-12T07:00:00Z'), // endpoint gone: never claimed, not a backlog
        ],
        nextCursor: null,
      },
      [ep('active', 'ACTIVE'), ep('paused', 'PAUSED')],
      NOW,
    );
    expect(h).toEqual({
      overdue: 1,
      oldestOverdueMs: 600_000,
      waitingOnPaused: 1,
      orphaned: 1,
      sampled: false,
      state: 'backlog',
    });
    expect(deriveQueueHealth({ items: [], nextCursor: null }, [], NOW).state).toBe('idle');
  });

  it('formats ages', () => {
    expect(formatAge(42_000)).toBe('42s');
    expect(formatAge(600_000)).toBe('10 min');
    expect(formatAge(3 * 3600_000 + 5 * 60_000)).toBe('3 h 5 min');
  });
});
