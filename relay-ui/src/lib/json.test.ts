import { describe, expect, it } from 'vitest';
import { analyzePayload } from './json';

describe('analyzePayload', () => {
  it('accepts any JSON value except null and measures compact UTF-8 bytes', () => {
    expect(analyzePayload('{ "a" : 1 }')).toEqual({ ok: true, value: { a: 1 }, compactBytes: 7 });
    expect(analyzePayload('"é"')).toMatchObject({ ok: true, compactBytes: 4 });
    expect(analyzePayload('[1, 2]')).toMatchObject({ ok: true });
  });

  it('rejects empty, null and invalid JSON with a readable message', () => {
    expect(analyzePayload('  ')).toEqual({ ok: false, error: expect.stringContaining('required') });
    expect(analyzePayload('null')).toEqual({
      ok: false,
      error: expect.stringContaining('cannot be null'),
    });
    const bad = analyzePayload('{"a":');
    expect(bad.ok).toBe(false);
    expect(!bad.ok && bad.error).toMatch(/^Invalid JSON/);
  });
});
