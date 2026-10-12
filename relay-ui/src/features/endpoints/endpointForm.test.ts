import { describe, expect, it } from 'vitest';
import type { Endpoint } from '@/api/schemas';
import {
  describeFilter,
  endpointFormSchema,
  filterHasMisplacedStar,
  toCreateRequest,
  toUpdateRequest,
} from './endpointForm';

const base: Endpoint = {
  id: '00000000-0000-4000-8000-000000000001',
  url: 'https://a.example.com/h',
  description: null,
  eventTypeFilter: 'order.*',
  status: 'ACTIVE',
  consecutiveFailures: 0,
  pausedReason: null,
  createdAt: '2026-10-12T09:00:00Z',
  updatedAt: '2026-10-12T09:00:00Z',
};

describe('endpoint form mapping', () => {
  it('omits a blank filter and description on create (backend rejects eventTypeFilter "")', () => {
    expect(
      toCreateRequest({ url: ' https://x.example.com ', description: ' ', eventTypeFilter: '' }),
    ).toEqual({
      url: 'https://x.example.com',
    });
    expect(
      toCreateRequest({
        url: 'https://x.example.com',
        description: 'd',
        eventTypeFilter: 'order.*',
      }),
    ).toEqual({
      url: 'https://x.example.com',
      description: 'd',
      eventTypeFilter: 'order.*',
    });
  });

  it('sends only changed fields on edit; a cleared filter becomes "*"', () => {
    expect(
      toUpdateRequest(base, { url: base.url, description: '', eventTypeFilter: 'order.*' }),
    ).toEqual({});
    expect(
      toUpdateRequest(base, { url: base.url, description: 'new', eventTypeFilter: '' }),
    ).toEqual({
      description: 'new',
      eventTypeFilter: '*',
    });
    expect(
      toUpdateRequest(
        { ...base, description: 'old' },
        { url: base.url, description: '', eventTypeFilter: 'order.*' },
      ),
    ).toEqual({
      description: '',
    });
  });

  it('validates like the backend regex', () => {
    expect(
      endpointFormSchema.safeParse({
        url: 'https://x.io',
        description: '',
        eventTypeFilter: 'Bad Filter',
      }).success,
    ).toBe(false);
    expect(
      endpointFormSchema.safeParse({ url: 'ftp://x.io', description: '', eventTypeFilter: '' })
        .success,
    ).toBe(false);
    expect(
      endpointFormSchema.safeParse({
        url: 'https://x.io',
        description: '',
        eventTypeFilter: 'order.*',
      }).success,
    ).toBe(true);
  });

  it('explains filters the way EventTypeMatcher applies them', () => {
    expect(describeFilter('')).toBe('Receives every event type.');
    expect(describeFilter('*')).toBe('Receives every event type.');
    expect(describeFilter('order.*')).toContain('starting with "order."');
    expect(describeFilter('order.*')).toContain('nor "orders.x"');
    expect(describeFilter('order.paid')).toBe('Receives only the exact type "order.paid".');
    expect(describeFilter('order*')).toMatch(/^Warning/);
    expect(filterHasMisplacedStar('order*')).toBe(true);
    expect(filterHasMisplacedStar('*.paid')).toBe(true);
    expect(filterHasMisplacedStar('order.*')).toBe(false);
    expect(filterHasMisplacedStar('*')).toBe(false);
  });
});
