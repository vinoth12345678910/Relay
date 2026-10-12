import { z } from 'zod';
import { EVENT_TYPE_PATTERN } from '@/api/schemas';
import { analyzePayload } from '@/lib/json';

/** Header values must be visible ASCII, or fetch() throws before sending. */
const IDEMPOTENCY_KEY_PATTERN = /^[\x21-\x7e]*$/;
export const MAX_IDEMPOTENCY_KEY = 255;

export const publishFormSchema = z.object({
  type: z
    .string()
    .trim()
    .regex(
      EVENT_TYPE_PATTERN,
      '1–100 chars: lowercase letters, digits, ".", "_" or "-" (e.g. order.paid)',
    ),
  payload: z.string().superRefine((text, ctx) => {
    const a = analyzePayload(text);
    if (!a.ok) ctx.addIssue({ code: 'custom', message: a.error });
  }),
  idempotencyKey: z
    .string()
    .trim()
    .max(MAX_IDEMPOTENCY_KEY, `At most ${MAX_IDEMPOTENCY_KEY} characters`)
    .regex(IDEMPOTENCY_KEY_PATTERN, 'Use printable ASCII without spaces'),
});
export type PublishFormInput = z.input<typeof publishFormSchema>;
export type PublishFormValues = z.output<typeof publishFormSchema>;

export const DEFAULT_PAYLOAD = '{\n  "orderId": 42,\n  "amount": 1999,\n  "currency": "EUR"\n}';
