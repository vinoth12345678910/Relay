import { describe, expect, it } from 'vitest';
import { analyzePayload, sizeVerdict } from './json';

describe('sizeVerdict', () => {
  it('warns from 90% and flags over the limit', () => {
    expect(sizeVerdict(89, 100)).toMatchObject({ near: false, over: false });
    expect(sizeVerdict(90, 100)).toMatchObject({ near: true, over: false });
    expect(sizeVerdict(101, 100)).toMatchObject({ near: true, over: true });
  });
});

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
