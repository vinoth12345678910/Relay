import { utf8Bytes } from './bytes';

export type PayloadAnalysis =
  { ok: true; value: unknown; compactBytes: number } | { ok: false; error: string };

/**
 * Parses the editor text. `compactBytes` approximates what the backend measures: it re-serializes
 * the payload with Jackson (compact) before the 256 KB check, which JSON.stringify mirrors closely
 * (number formatting like 1.0 vs 1 can differ by a few bytes), so the server stays authoritative.
 */
export function analyzePayload(text: string): PayloadAnalysis {
  if (text.trim() === '')
    return { ok: false, error: 'Payload is required (any JSON value except null).' };
  let value: unknown;
  try {
    value = JSON.parse(text);
  } catch (e) {
    return {
      ok: false,
      error: `Invalid JSON: ${e instanceof Error ? e.message : 'could not parse'}`,
    };
  }
  if (value === null) return { ok: false, error: 'Payload cannot be null: Relay rejects it.' };
  return { ok: true, value, compactBytes: utf8Bytes(JSON.stringify(value)) };
}

const GUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
/** Relay ids are UUIDs; anything else would only earn a 400 from the API. */
export const isId = (v: string | null | undefined): v is string =>
  typeof v === 'string' && GUID.test(v);

export type SizeVerdict = { ratio: number; over: boolean; near: boolean };

/** Where a compact payload size sits against the 256 KB limit (warn from 90%). */
export function sizeVerdict(compactBytes: number, limit: number): SizeVerdict {
  const ratio = compactBytes / limit;
  return { ratio, over: ratio > 1, near: ratio >= 0.9 };
}

export function prettyJson(value: unknown): string {
  return JSON.stringify(value, null, 2);
}
