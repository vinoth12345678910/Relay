import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import type { Endpoint } from '@/api/schemas';
import { Button } from '@/components/ui/Button';
import { Dialog } from '@/components/ui/Dialog';
import { Field, inputClass, monoInputClass } from '@/components/ui/Field';
import { applyServerErrors } from '@/lib/formErrors';
import {
  describeFilter,
  endpointFormSchema,
  filterHasMisplacedStar,
  formValuesFrom,
  type EndpointFormValues,
} from './endpointForm';

const FIELDS = ['url', 'description', 'eventTypeFilter'] as const;

/** Create (endpoint = null) or edit an endpoint. `onSubmit` rejects with the API error to map it. */
export function EndpointFormDialog({
  open,
  endpoint,
  onOpenChange,
  onSubmit,
}: {
  open: boolean;
  endpoint: Endpoint | null;
  onOpenChange: (open: boolean) => void;
  onSubmit: (values: EndpointFormValues) => Promise<unknown>;
}) {
  const form = useForm<EndpointFormValues>({
    resolver: zodResolver(endpointFormSchema),
    defaultValues: formValuesFrom(endpoint),
  });
  const {
    register,
    handleSubmit,
    reset,
    setError,
    watch,
    formState: { errors, isSubmitting },
  } = form;

  useEffect(() => {
    if (open) reset(formValuesFrom(endpoint));
  }, [open, endpoint, reset]);

  const filter = watch('eventTypeFilter');
  const submit = handleSubmit(async (v) => {
    try {
      await onSubmit(v);
    } catch (e) {
      applyServerErrors(e, setError, FIELDS);
    }
  });

  return (
    <Dialog
      open={open}
      onOpenChange={onOpenChange}
      title={endpoint ? 'Edit endpoint' : 'Add endpoint'}
      description={
        endpoint ? 'Only changed fields are sent.' : 'Relay will POST signed webhooks to this URL.'
      }
      footer={
        <>
          <Button onClick={() => onOpenChange(false)} disabled={isSubmitting}>
            Cancel
          </Button>
          <Button variant="primary" type="submit" form="endpoint-form" loading={isSubmitting}>
            {endpoint ? 'Save changes' : 'Add endpoint'}
          </Button>
        </>
      }
    >
      <form id="endpoint-form" onSubmit={submit} noValidate className="flex flex-col gap-4">
        <Field
          label="URL"
          error={errors.url?.message}
          hint="Public http(s) URL. Relay rejects private, loopback and link-local addresses."
        >
          <input
            {...register('url')}
            className={monoInputClass}
            placeholder="https://example.com/webhooks/relay"
            inputMode="url"
            autoComplete="off"
          />
        </Field>
        <Field label="Description" optional error={errors.description?.message}>
          <input
            {...register('description')}
            className={inputClass}
            placeholder="Order service (prod)"
            autoComplete="off"
          />
        </Field>
        <Field
          label="Event type filter"
          optional
          error={errors.eventTypeFilter?.message}
          hint={
            <span className={filterHasMisplacedStar(filter) ? 'text-warning' : undefined}>
              {describeFilter(filter)} Use <code className="font-mono">*</code> for everything,{' '}
              <code className="font-mono">order.*</code> for a prefix, or an exact type like{' '}
              <code className="font-mono">order.paid</code>. Empty means{' '}
              <code className="font-mono">*</code>.
            </span>
          }
        >
          <input
            {...register('eventTypeFilter')}
            className={monoInputClass}
            placeholder="*"
            autoComplete="off"
            spellCheck={false}
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
      </form>
    </Dialog>
  );
}
