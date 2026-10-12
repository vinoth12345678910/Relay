import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { MAX_PAUSE_REASON, type Endpoint } from '@/api/schemas';
import { Button } from '@/components/ui/Button';
import { Dialog } from '@/components/ui/Dialog';
import { Field, inputClass } from '@/components/ui/Field';
import { applyServerErrors } from '@/lib/formErrors';
import { cn } from '@/lib/cn';
import { pauseFormSchema, type PauseFormValues } from './endpointForm';

export function PauseDialog({
  endpoint,
  onOpenChange,
  onSubmit,
}: {
  endpoint: Endpoint | null;
  onOpenChange: (open: boolean) => void;
  onSubmit: (reason: string) => Promise<unknown>;
}) {
  const { register, handleSubmit, reset, setError, watch, formState } = useForm<PauseFormValues>({
    resolver: zodResolver(pauseFormSchema),
    defaultValues: { reason: '' },
  });
  useEffect(() => {
    if (endpoint) reset({ reason: '' });
  }, [endpoint, reset]);
  const length = watch('reason').length;

  const submit = handleSubmit(async (v) => {
    try {
      await onSubmit(v.reason);
    } catch (e) {
      applyServerErrors(e, setError, ['reason']);
    }
  });

  return (
    <Dialog
      open={endpoint !== null}
      onOpenChange={onOpenChange}
      title="Pause endpoint"
      description="New events still queue for this endpoint, but nothing is sent until you resume it."
      footer={
        <>
          <Button onClick={() => onOpenChange(false)} disabled={formState.isSubmitting}>
            Cancel
          </Button>
          <Button
            variant="primary"
            type="submit"
            form="pause-form"
            loading={formState.isSubmitting}
          >
            Pause
          </Button>
        </>
      }
    >
      <form id="pause-form" onSubmit={submit} noValidate className="flex flex-col gap-3">
        <p className="truncate font-mono text-xs text-muted">{endpoint?.url}</p>
        <Field
          label="Reason"
          optional
          error={formState.errors.reason?.message}
          hint={
            <span className={cn(length > MAX_PAUSE_REASON && 'text-danger')}>
              {length}/{MAX_PAUSE_REASON}. Shown to your team. Defaults to “Paused manually”.
            </span>
          }
        >
          <textarea
            {...register('reason')}
            rows={3}
            className={cn(inputClass, 'h-auto py-2')}
            placeholder="Receiver maintenance until 14:00"
          />
        </Field>
        {formState.errors.root?.server?.message && (
          <p role="alert" className="text-sm text-danger">
            {formState.errors.root.server.message}
          </p>
        )}
      </form>
    </Dialog>
  );
}
