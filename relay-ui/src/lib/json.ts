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

export function prettyJson(value: unknown): string {
  return JSON.stringify(value, null, 2);
}
