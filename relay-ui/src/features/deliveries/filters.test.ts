import { describe, expect, it } from 'vitest';
import {
  canReplay,
  filtersFromParams,
  mergePages,
  replayTone,
  withDelivery,
  withFilter,
} from './filters';

describe('delivery filters', () => {
  it('reads valid filters from the URL and drops ones the API would reject', () => {
    expect(
      filtersFromParams(
        new URLSearchParams('status=dead&endpointId=00000000-0000-4000-8000-000000000001'),
      ),
    ).toEqual({
      status: 'DEAD',
      endpointId: '00000000-0000-4000-8000-000000000001',
    });
    expect(filtersFromParams(new URLSearchParams('status=BOGUS&endpointId=not-a-uuid'))).toEqual(
      {},
    );
  });

  it('updates one param at a time and keeps the others', () => {
    const p = withFilter(new URLSearchParams('status=DEAD&delivery=x'), 'endpointId', 'e1');
    expect(p.toString()).toBe('status=DEAD&delivery=x&endpointId=e1');
    expect(withFilter(p, 'status', '').toString()).toBe('delivery=x&endpointId=e1');
    expect(withDelivery(p, null).toString()).toBe('status=DEAD&endpointId=e1');
  });

  it('merges cursor pages without duplicates, keeping order', () => {
    expect(
      mergePages([
        { items: [{ id: 'a' }, { id: 'b' }] },
        { items: [{ id: 'b' }, { id: 'c' }] },
      ]).map((x) => x.id),
    ).toEqual(['a', 'b', 'c']);
  });

  it('presents a replay as a warning when the endpoint is paused or deleted', () => {
    const eps = [
      { id: 'a', status: 'ACTIVE' },
      { id: 'p', status: 'PAUSED' },
    ];
    expect(replayTone(eps, 'a')).toBe('success');
    expect(replayTone(eps, 'p')).toBe('warning');
    expect(replayTone(eps, 'gone')).toBe('warning');
    expect(replayTone(undefined, 'a')).toBe('success');
  });

  it('allows replay only for DEAD and SUCCEEDED', () => {
    expect(['PENDING', 'IN_FLIGHT', 'SUCCEEDED', 'DEAD'].map((s) => canReplay(s as never))).toEqual(
      [false, false, true, true],
    );
  });
});
