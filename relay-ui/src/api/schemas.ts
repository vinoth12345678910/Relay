import { z } from 'zod';

/*
 * Single source of truth for the Relay API shapes, written by hand from the Java records in
 * relay-server/src/main/java/com/relay/server/{app,endpoint,event,delivery,common}.
 * The client parses every response with these, the MSW mocks validate every response with these,
 * and src/api/contract.test.ts fails if a Java record's components drift from the keys here.
 */

/** java.time.Instant, serialized by Jackson 3 as an ISO-8601 string. */
const instant = z
  .string()
  .refine((s) => !Number.isNaN(Date.parse(s)), 'expected an ISO-8601 timestamp');
/** java.util.UUID. `guid` accepts any 8-4-4-4-12 hex id (no RFC variant check). */
const id = z.guid();
const int = z.number().int();

export const ENDPOINT_STATUSES = ['ACTIVE', 'PAUSED', 'DISABLED'] as const;
export const DELIVERY_STATUSES = ['PENDING', 'IN_FLIGHT', 'SUCCEEDED', 'DEAD'] as const;
export const EndpointStatusSchema = z.enum(ENDPOINT_STATUSES);
export const DeliveryStatusSchema = z.enum(DELIVERY_STATUSES);

/** Backend validation rules, mirrored exactly (EventService.TYPE_PATTERN, CreateEndpointRequest @Pattern). */
export const EVENT_TYPE_PATTERN = /^[a-z0-9._-]{1,100}$/;
export const EVENT_TYPE_FILTER_PATTERN = /^[a-z0-9._*-]+$/;
export const MAX_PAYLOAD_BYTES = 256 * 1024;
export const MAX_PAUSE_REASON = 500;
export const MAX_DELIVERY_PAGE = 200;

// ---------- apps (app/*.java) ----------

export const CreateAppRequestSchema = z.object({ name: z.string() });
export const CreateAppResponseSchema = z.object({
  id,
  name: z.string(),
  apiKey: z.string(),
  apiKeyPrefix: z.string(),
});
export const AppResponseSchema = z.object({ id, name: z.string(), createdAt: instant });

// ---------- endpoints (endpoint/*.java) ----------

export const EndpointResponseSchema = z.object({
  id,
  url: z.string(),
  description: z.string().nullable(),
  eventTypeFilter: z.string(),
  status: EndpointStatusSchema,
  consecutiveFailures: int,
  pausedReason: z.string().nullable(),
  createdAt: instant,
  updatedAt: instant,
});
export const CreateEndpointResponseSchema = EndpointResponseSchema.extend({
  signingSecret: z.string(),
});
export const EndpointListSchema = z.array(EndpointResponseSchema);

export const CreateEndpointRequestSchema = z.object({
  url: z.string(),
  description: z.string().optional(),
  eventTypeFilter: z.string().optional(),
});
export const UpdateEndpointRequestSchema = z.object({
  url: z.string().optional(),
  description: z.string().optional(),
  eventTypeFilter: z.string().optional(),
});
export const PauseEndpointRequestSchema = z.object({ reason: z.string().optional() });

// ---------- events (event/*.java) ----------

export const PublishEventRequestSchema = z.object({ type: z.string(), payload: z.json() });
export const PublishEventResponseSchema = z.object({
  id,
  type: z.string(),
  createdAt: instant,
  deliveryCount: int,
  replayed: z.boolean(),
});
export const DeliverySummarySchema = z.object({
  id,
  endpointId: id,
  status: DeliveryStatusSchema,
  attemptCount: int.nullable(),
  nextAttemptAt: instant.nullable(),
  lastStatusCode: int.nullable(),
});
export const EventResponseSchema = z.object({
  id,
  type: z.string(),
  payload: z.json(),
  createdAt: instant,
  deliveries: z.array(DeliverySummarySchema),
});

// ---------- deliveries (delivery/*.java) ----------

export const DeliveryDetailSchema = z.object({
  id,
  eventId: id,
  endpointId: id,
  status: DeliveryStatusSchema,
  attemptCount: int,
  attemptBase: int,
  nextAttemptAt: instant,
  lastStatusCode: int.nullable(),
  createdAt: instant,
  updatedAt: instant,
});
export const DeliveryPageSchema = z.object({
  items: z.array(DeliveryDetailSchema),
  nextCursor: z.string().nullable(),
});
export const AttemptViewSchema = z.object({
  attemptNumber: int,
  startedAt: instant,
  durationMs: int,
  statusCode: int.nullable(),
  error: z.string().nullable(),
  responseSnippet: z.string().nullable(),
});
export const AttemptListSchema = z.array(AttemptViewSchema);
export const ReplayResponseSchema = z.object({
  delivery: DeliveryDetailSchema,
  message: z.string(),
});

// ---------- errors (common/ApiError.java, FieldError.java) ----------

export const FieldErrorSchema = z.object({ field: z.string(), message: z.string() });
export const ApiErrorBodySchema = z.object({
  timestamp: instant,
  status: int,
  error: z.string(),
  code: z.string(),
  message: z.string(),
  path: z.string(),
  /** Omitted by the server when empty (@JsonInclude NON_EMPTY). */
  fieldErrors: z.array(FieldErrorSchema).optional(),
});

export type EndpointStatus = z.infer<typeof EndpointStatusSchema>;
export type DeliveryStatus = z.infer<typeof DeliveryStatusSchema>;
export type CreateAppResponse = z.infer<typeof CreateAppResponseSchema>;
export type Endpoint = z.infer<typeof EndpointResponseSchema>;
export type CreateEndpointRequest = z.infer<typeof CreateEndpointRequestSchema>;
export type UpdateEndpointRequest = z.infer<typeof UpdateEndpointRequestSchema>;
export type PublishEventResponse = z.infer<typeof PublishEventResponseSchema>;
export type DeliverySummary = z.infer<typeof DeliverySummarySchema>;
export type Delivery = z.infer<typeof DeliveryDetailSchema>;
export type DeliveryPage = z.infer<typeof DeliveryPageSchema>;
export type Attempt = z.infer<typeof AttemptViewSchema>;
export type FieldError = z.infer<typeof FieldErrorSchema>;
export type ApiErrorBody = z.infer<typeof ApiErrorBodySchema>;
