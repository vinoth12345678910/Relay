import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation } from '@tanstack/react-query';
import { createApp } from '@/api/client';
import { describeError, isApiError } from '@/api/errors';
import type { CreateAppResponse } from '@/api/schemas';
import { Button } from '@/components/ui/Button';
import { Field, inputClass, monoInputClass } from '@/components/ui/Field';
import { ApiKeyOnceDialog } from './ApiKeyOnceDialog';
import { createAppSchema, type CreateAppValues } from './schema';

/**
 * Creates an app with the admin token. The token lives only in this form, is sent once, and is
 * cleared from the form whatever the outcome. The new API key lives only in the dialog's state.
 */
export function CreateAppPanel({
  baseUrl,
  onConnect,
}: {
  baseUrl: string;
  onConnect: (apiKey: string) => void;
}) {
  const [created, setCreated] = useState<CreateAppResponse | null>(null);
  const form = useForm<CreateAppValues>({
    resolver: zodResolver(createAppSchema),
    defaultValues: { name: '', adminToken: '' },
  });
  const {
    register,
    handleSubmit,
    setValue,
    setError,
    formState: { errors },
  } = form;

  const mutation = useMutation({
    mutationFn: (v: CreateAppValues) =>
      createApp({ name: v.name, adminToken: v.adminToken, baseUrl }),
    onSettled: () => setValue('adminToken', ''),
    onSuccess: (app) => {
      form.reset({ name: '', adminToken: '' });
      setCreated(app);
    },
    onError: (e) => {
      if (isApiError(e, 401))
        setError('adminToken', { message: 'Relay rejected the admin token.' });
      else {
        const d = describeError(e);
        const nameErr = d.fieldErrors.find((f) => f.field === 'name');
        setError(nameErr ? 'name' : 'root', {
          message: nameErr?.message ?? `${d.title}: ${d.detail}`,
        });
      }
    },
  });

  return (
    <>
      <form
        onSubmit={handleSubmit((v) => mutation.mutate(v))}
        noValidate
        className="flex flex-col gap-4"
      >
        <Field label="App name" error={errors.name?.message}>
          <input
            {...register('name')}
            className={inputClass}
            placeholder="my-app"
            autoComplete="off"
          />
        </Field>
        <Field
          label="Admin token"
          error={errors.adminToken?.message}
          hint="RELAY_ADMIN_TOKEN on the server. Used for this one request, never stored."
        >
          <input
            {...register('adminToken')}
            type="password"
            className={monoInputClass}
            autoComplete="off"
            spellCheck={false}
          />
        </Field>
        {errors.root?.message && (
          <p role="alert" className="text-sm text-danger">
            {errors.root.message}
          </p>
        )}
        <Button type="submit" loading={mutation.isPending}>
          Create app
        </Button>
      </form>
      <ApiKeyOnceDialog
        app={created}
        onClose={() => setCreated(null)}
        onConnect={(key) => {
          setCreated(null);
          onConnect(key);
        }}
      />
    </>
  );
}
