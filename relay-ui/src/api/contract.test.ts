import { readFileSync, existsSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import type { z } from 'zod';
import * as S from './schemas';

/*
 * Contract drift guard: reads the Java DTO records in ../relay-server and checks that their
 * component names match the keys of the zod schemas this UI parses responses with.
 * Rename or add a field in Java and this test fails until the UI is updated.
 */
const JAVA_ROOT = resolve(__dirname, '../../../relay-server/src/main/java/com/relay/server');

function stripAnnotations(src: string): string {
  let out = '';
  for (let i = 0; i < src.length; i++) {
    if (src[i] === '@' && /[A-Za-z]/.test(src[i + 1] ?? '')) {
      i++;
      while (i < src.length && /[\w.]/.test(src[i] ?? '')) i++;
      while (src[i] === ' ') i++;
      if (src[i] === '(') {
        let depth = 0;
        for (; i < src.length; i++) {
          if (src[i] === '"') {
            i++;
            while (i < src.length && src[i] !== '"') i += src[i] === '\\' ? 2 : 1;
          } else if (src[i] === '(') depth++;
          else if (src[i] === ')' && --depth === 0) break;
        }
      } else i--;
      continue;
    }
    out += src[i];
  }
  return out;
}

/** Component names of `record Name(...)` in the given file. */
export function recordComponents(relPath: string): string[] {
  const raw = readFileSync(resolve(JAVA_ROOT, relPath), 'utf8')
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/\/\/.*$/gm, '');
  const src = stripAnnotations(raw);
  const m = /\brecord\s+\w+\s*\(([\s\S]*?)\)\s*(?:implements[^{]*)?\{/.exec(src);
  if (!m?.[1]) throw new Error(`no record found in ${relPath}`);
  const parts: string[] = [];
  let depth = 0;
  let cur = '';
  for (const ch of m[1]) {
    if (ch === '<') depth++;
    if (ch === '>') depth--;
    if (ch === ',' && depth === 0) {
      parts.push(cur);
      cur = '';
    } else cur += ch;
  }
  parts.push(cur);
  return parts.map((p) => p.trim().split(/\s+/).pop() ?? '').filter((n) => n !== '');
}

function enumConstants(relPath: string): string[] {
  const src = readFileSync(resolve(JAVA_ROOT, relPath), 'utf8')
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/\/\/.*$/gm, '');
  const m = /\benum\s+\w+\s*\{([^;}]*)/.exec(src);
  if (!m?.[1]) throw new Error(`no enum found in ${relPath}`);
  return m[1]
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean);
}

const keys = (schema: z.ZodObject) => Object.keys(schema.shape).sort();

const RECORDS: Array<[string, z.ZodObject]> = [
  ['app/CreateAppRequest.java', S.CreateAppRequestSchema],
  ['app/CreateAppResponse.java', S.CreateAppResponseSchema],
  ['app/AppResponse.java', S.AppResponseSchema],
  ['endpoint/EndpointResponse.java', S.EndpointResponseSchema],
  ['endpoint/CreateEndpointResponse.java', S.CreateEndpointResponseSchema],
  ['endpoint/CreateEndpointRequest.java', S.CreateEndpointRequestSchema],
  ['endpoint/UpdateEndpointRequest.java', S.UpdateEndpointRequestSchema],
  ['endpoint/PauseEndpointRequest.java', S.PauseEndpointRequestSchema],
  ['event/PublishEventRequest.java', S.PublishEventRequestSchema],
  ['event/PublishEventResponse.java', S.PublishEventResponseSchema],
  ['event/EventResponse.java', S.EventResponseSchema],
  ['event/DeliverySummary.java', S.DeliverySummarySchema],
  ['delivery/DeliveryDetail.java', S.DeliveryDetailSchema],
  ['delivery/DeliveryPage.java', S.DeliveryPageSchema],
  ['delivery/AttemptView.java', S.AttemptViewSchema],
  ['delivery/ReplayResponse.java', S.ReplayResponseSchema],
  ['common/ApiError.java', S.ApiErrorBodySchema],
  ['common/FieldError.java', S.FieldErrorSchema],
];

describe('API contract vs relay-server Java records', () => {
  it('can see the backend sources (monorepo checkout)', () => {
    expect(existsSync(JAVA_ROOT), `expected backend sources at ${JAVA_ROOT}`).toBe(true);
  });

  it.each(RECORDS)('%s matches its zod schema', (file, schema) => {
    expect(keys(schema)).toEqual(recordComponents(file).sort());
  });

  it('status enums match', () => {
    expect([...S.DELIVERY_STATUSES]).toEqual(enumConstants('delivery/DeliveryStatus.java'));
    expect([...S.ENDPOINT_STATUSES]).toEqual(enumConstants('endpoint/EndpointStatus.java'));
  });

  it('validation patterns and limits match the backend source', () => {
    const eventService = readFileSync(resolve(JAVA_ROOT, 'event/EventService.java'), 'utf8');
    expect(eventService).toContain(`Pattern.compile("${S.EVENT_TYPE_PATTERN.source}")`);
    expect(eventService).toContain('MAX_PAYLOAD_BYTES = 256 * 1024');
    const createReq = readFileSync(
      resolve(JAVA_ROOT, 'endpoint/CreateEndpointRequest.java'),
      'utf8',
    );
    expect(createReq).toContain(`regexp = "${S.EVENT_TYPE_FILTER_PATTERN.source}"`);
    const deliveryService = readFileSync(
      resolve(JAVA_ROOT, 'delivery/DeliveryService.java'),
      'utf8',
    );
    expect(deliveryService).toContain(`MAX_LIMIT = ${S.MAX_DELIVERY_PAGE}`);
    const pause = readFileSync(resolve(JAVA_ROOT, 'endpoint/PauseEndpointRequest.java'), 'utf8');
    expect(pause).toContain(`max = ${S.MAX_PAUSE_REASON}`);
  });

  it('headers the UI relies on are the ones the controllers use', () => {
    const ctrl = readFileSync(resolve(JAVA_ROOT, 'event/EventController.java'), 'utf8');
    expect(ctrl).toContain('"Idempotent-Replayed"');
    expect(ctrl).toContain('"Idempotency-Key"');
  });

  it('parser handles annotations with commas and parentheses', () => {
    expect(recordComponents('endpoint/CreateEndpointRequest.java')).toEqual([
      'url',
      'description',
      'eventTypeFilter',
    ]);
  });
});
