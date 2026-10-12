import { describe, expect, it, vi } from 'vitest';
import { ApiError } from '@/api/http';
import { applyServerErrors } from './formErrors';

const body = (fieldErrors: { field: string; message: string }[]) => ({
  timestamp: '2026-10-12T10:00:00Z',
  status: 400,
  error: 'Bad Request',
  code: 'VALIDATION_ERROR',
  message: 'Validation failed',
  path: '/api/v1/endpoints',
  fieldErrors,
});

describe('applyServerErrors', () => {
  it('maps known field errors onto inputs with friendly text', () => {
    const setError = vi.fn();
    const placed = applyServerErrors(
      new ApiError(400, body([{ field: 'eventTypeFilter', message: 'invalid event type filter' }])),
      setError,
      ['url', 'eventTypeFilter'],
    );
    expect(placed).toBe(true);
    expect(setError).toHaveBeenCalledWith(
      'eventTypeFilter',
      expect.objectContaining({ message: expect.stringContaining('for example "*", "order.*"') }),
    );
  });

  it('puts unknown fields and non-field errors on the form root, never as JSON', () => {
    const setError = vi.fn();
    applyServerErrors(
      new ApiError(409, {
        ...body([]),
        status: 409,
        code: 'CONFLICT',
        message: 'Something conflicts',
      }),
      setError,
      ['url'],
    );
    expect(setError).toHaveBeenCalledWith('root.server', {
      type: 'server',
      message: 'Conflict. Something conflicts.',
    });
    const setError2 = vi.fn();
    applyServerErrors(new ApiError(400, body([{ field: 'other', message: 'nope' }])), setError2, [
      'url',
    ]);
    expect(setError2).toHaveBeenCalledWith('root.server', {
      type: 'server',
      message: 'other: nope',
    });
  });
});
