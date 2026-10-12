import { z } from 'zod';
import {
  EVENT_TYPE_FILTER_PATTERN,
  MAX_PAUSE_REASON,
  type CreateEndpointRequest,
  type Endpoint,
  type UpdateEndpointRequest,
} from '@/api/schemas';

/** Form rules mirror CreateEndpointRequest / UpdateEndpointRequest; the SSRF check stays server-side. */
export const endpointFormSchema = z.object({
  url: z
    .string()
    .trim()
    .min(1, 'URL is required')
    .refine((v) => {
      try {
        const u = new URL(v);
        return u.protocol === 'http:' || u.protocol === 'https:';
      } catch {
        return false;
      }
    }, 'Enter a full http:// or https:// URL'),
  description: z.string(),
  eventTypeFilter: z
    .string()
    .trim()
    .refine(
      (v) => v === '' || EVENT_TYPE_FILTER_PATTERN.test(v),
      'Use lowercase letters, digits, ".", "_", "-" and "*"',
    ),
});
export type EndpointFormValues = z.infer<typeof endpointFormSchema>;

export const pauseFormSchema = z.object({
  reason: z.string().max(MAX_PAUSE_REASON, `At most ${MAX_PAUSE_REASON} characters`),
});
export type PauseFormValues = z.infer<typeof pauseFormSchema>;

export function formValuesFrom(e: Endpoint | null): EndpointFormValues {
  return {
    url: e?.url ?? '',
    description: e?.description ?? '',
    eventTypeFilter: e?.eventTypeFilter ?? '',
  };
}

/**
 * Builds the POST body. A blank filter or description is omitted rather than sent as "":
 * the backend's @Pattern rejects "" (400) even though a missing filter defaults to "*".
 */
export function toCreateRequest(v: EndpointFormValues): CreateEndpointRequest {
  const body: CreateEndpointRequest = { url: v.url.trim() };
  if (v.description.trim() !== '') body.description = v.description.trim();
  if (v.eventTypeFilter.trim() !== '') body.eventTypeFilter = v.eventTypeFilter.trim();
  return body;
}

/**
 * Builds the PATCH body with only the changed fields (null/absent = unchanged server-side).
 * A cleared filter is sent as "*" (what blank means on create); a cleared description as "".
 */
export function toUpdateRequest(original: Endpoint, v: EndpointFormValues): UpdateEndpointRequest {
  const body: UpdateEndpointRequest = {};
  const url = v.url.trim();
  const description = v.description.trim();
  const filter = v.eventTypeFilter.trim() === '' ? '*' : v.eventTypeFilter.trim();
  if (url !== original.url) body.url = url;
  if (description !== (original.description ?? '')) body.description = description;
  if (filter !== original.eventTypeFilter) body.eventTypeFilter = filter;
  return body;
}

/** Plain-language meaning of a filter, following EventTypeMatcher exactly. */
export function describeFilter(raw: string): string {
  const f = raw.trim();
  if (f === '' || f === '*') return 'Receives every event type.';
  if (f.endsWith('.*')) {
    const prefix = f.slice(0, -1);
    return `Receives types starting with "${prefix}" (so not "${prefix.slice(0, -1)}" itself, nor "${prefix.slice(0, -1)}s.x").`;
  }
  if (f.includes('*')) {
    return `Warning: "*" only works alone or as a trailing ".*". This filter matches the literal type "${f}" only.`;
  }
  return `Receives only the exact type "${f}".`;
}

export const filterHasMisplacedStar = (raw: string) => {
  const f = raw.trim();
  return f.includes('*') && f !== '*' && !(f.endsWith('.*') && !f.slice(0, -2).includes('*'));
};
