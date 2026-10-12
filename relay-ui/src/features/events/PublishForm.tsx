import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Braces, Dices, Send } from 'lucide-react';
import { isApiError } from '@/api/errors';
import { Button } from '@/components/ui/Button';
import { Field, monoInputClass } from '@/components/ui/Field';
import { applyServerErrors } from '@/lib/formErrors';
import { analyzePayload, prettyJson } from '@/lib/json';
import { PayloadStatus, PayloadTextarea } from './PayloadEditor';
import {
  DEFAULT_PAYLOAD,
  publishFormSchema,
  type PublishFormInput,
  type PublishFormValues,
} from './publishForm';

type Submit = (v: { type: string; payload: unknown; idempotencyKey?: string }) => Promise<unknown>;

export function PublishForm({ onPublish, newKey }: { onPublish: Submit; newKey: () => string }) {
  const form = useForm<PublishFormInput, unknown, PublishFormValues>({
    resolver: zodResolver(publishFormSchema),
    mode: 'onTouched',
    defaultValues: { type: 'order.paid', payload: DEFAULT_PAYLOAD, idempotencyKey: '' },
  });
  const { register, handleSubmit, setValue, setError, watch, formState } = form;
  const { errors, isSubmitting } = formState;
  const payloadText = watch('payload');

  const submit = handleSubmit(async (v) => {
    const parsed = analyzePayload(v.payload);
    if (!parsed.ok) return;
    try {
      await onPublish({
        type: v.type,
        payload: parsed.value,
        idempotencyKey: v.idempotencyKey || undefined,
      });
    } catch (e) {
      if (isApiError(e, 409)) {
        setError('idempotencyKey', {
          message:
            'This key was already used with a different payload. Send the original payload, or generate a new key.',
        });
      } else if (isApiError(e, 413)) {
        setError('payload', {
          message:
            'Relay rejected the payload as too large (over 256 KB). Send less data or a reference to it.',
        });
      } else {
        applyServerErrors(e, setError, ['type', 'payload']);
      }
    }
  });

  const format = () => {
    const a = analyzePayload(payloadText);
    if (a.ok) setValue('payload', prettyJson(a.value), { shouldValidate: true });
  };

  return (
    <form onSubmit={submit} noValidate className="flex flex-col gap-4">
      <Field
        label="Event type"
        error={errors.type?.message}
        hint="Matches ^[a-z0-9._-]{1,100}$. Endpoints subscribe with filters like order.*"
      >
        <input
          {...register('type')}
          className={monoInputClass}
          autoComplete="off"
          spellCheck={false}
        />
      </Field>
      <Field
        label="Payload (JSON)"
        error={errors.payload?.message}
        hint={<PayloadStatus text={payloadText} />}
      >
        <PayloadTextarea {...register('payload')} />
      </Field>
      <div className="-mt-2 flex">
        <Button
          size="sm"
          variant="ghost"
          onClick={format}
          disabled={!analyzePayload(payloadText).ok}
        >
          <Braces aria-hidden className="size-3.5" /> Format JSON
        </Button>
      </div>
      <Field
        label="Idempotency-Key"
        optional
        error={errors.idempotencyKey?.message}
        hint="Re-sending the same key with the same payload returns the original event instead of creating a new one."
        addon={
          <Button
            onClick={() => setValue('idempotencyKey', newKey(), { shouldValidate: true })}
            aria-label="Generate idempotency key"
          >
            <Dices aria-hidden className="size-4" /> Generate
          </Button>
        }
      >
        <input
          {...register('idempotencyKey')}
          className={monoInputClass}
          autoComplete="off"
          spellCheck={false}
          placeholder="order-42-paid"
        />
      </Field>
      {errors.root?.server?.message && (
        <p
          role="alert"
          className="rounded-md border border-danger/40 bg-danger-bg p-3 text-sm text-danger"
        >
          {errors.root.server.message}
        </p>
      )}
      <div>
        <Button type="submit" variant="primary" loading={isSubmitting}>
          {!isSubmitting && <Send aria-hidden className="size-4" />} Publish event
        </Button>
      </div>
    </form>
  );
}
