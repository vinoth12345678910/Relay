import type { UseFormReturn } from 'react-hook-form';
import { Button } from '@/components/ui/Button';
import { Field, inputClass, monoInputClass } from '@/components/ui/Field';
import { Notice } from '@/components/ui/Feedback';
import { isCrossOrigin, type ConnectInput, type ConnectValues } from './schema';

export function ConnectForm({
  form,
  onSubmit,
  pending,
  formError,
}: {
  form: UseFormReturn<ConnectInput, unknown, ConnectValues>;
  onSubmit: (v: ConnectValues) => void;
  pending: boolean;
  formError: string | null;
}) {
  const {
    register,
    handleSubmit,
    watch,
    formState: { errors },
  } = form;
  const apiKey = watch('apiKey');
  const crossOrigin = isCrossOrigin(watch('baseUrl'), window.location.origin);

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate className="flex flex-col gap-4">
      <Field
        label="API base URL"
        optional
        error={errors.baseUrl?.message}
        hint="Leave empty to use this origin: the dev server (and the nginx image) proxy /api to relay-server."
      >
        <input
          {...register('baseUrl')}
          className={inputClass}
          placeholder="http://localhost:8080"
          inputMode="url"
          autoComplete="url"
        />
      </Field>
      {crossOrigin && (
        <Notice tone="warning">
          Cross-origin mode needs CORS on the backend. Relay&apos;s dev CORS config allows only{' '}
          <code className="font-mono">Authorization</code> and{' '}
          <code className="font-mono">Content-Type</code>, so sending an Idempotency-Key will fail.
          Prefer the same-origin proxy.
        </Notice>
      )}
      <Field
        label="API key"
        error={errors.apiKey?.message}
        hint={
          apiKey && !apiKey.trim().startsWith('rl_live_')
            ? 'Relay keys usually start with rl_live_. Check you pasted the whole key.'
            : 'Held in memory only. It is sent as a Bearer token and never put in a URL.'
        }
      >
        <input
          {...register('apiKey')}
          type="password"
          className={monoInputClass}
          autoComplete="off"
          spellCheck={false}
          placeholder="rl_live_…"
        />
      </Field>
      <label className="flex items-start gap-2 text-sm">
        <input
          type="checkbox"
          {...register('remember')}
          className="mt-0.5 size-4 accent-[var(--accent)]"
        />
        <span>
          Remember on this device
          <span className="block text-xs text-muted">
            Keeps the key in this tab&apos;s sessionStorage until the tab closes or you disconnect.
          </span>
        </span>
      </label>
      {formError && (
        <p
          role="alert"
          className="rounded-md border border-danger/40 bg-danger-bg p-3 text-sm text-danger"
        >
          {formError}
        </p>
      )}
      <Button type="submit" variant="primary" loading={pending}>
        Connect
      </Button>
    </form>
  );
}
