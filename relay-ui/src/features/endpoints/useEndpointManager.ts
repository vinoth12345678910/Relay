import { useState } from 'react';
import { describeError } from '@/api/errors';
import type { Endpoint } from '@/api/schemas';
import { useToast } from '@/components/ui/Toast';
import type { EndpointAction } from './EndpointActions';
import { toCreateRequest, toUpdateRequest, type EndpointFormValues } from './endpointForm';
import type { OneTimeSecret } from './SigningSecretDialog';
import {
  useCreateEndpoint,
  useDeleteEndpoint,
  usePauseEndpoint,
  useResumeEndpoint,
  useUpdateEndpoint,
} from './queries';

type FormTarget = { mode: 'create' } | { mode: 'edit'; endpoint: Endpoint } | null;

/** All endpoint-page interactions: which dialog is open, and what each submit does. */
export function useEndpointManager() {
  const toast = useToast();
  const [formTarget, setFormTarget] = useState<FormTarget>(null);
  const [pauseTarget, setPauseTarget] = useState<Endpoint | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<Endpoint | null>(null);
  const [secret, setSecret] = useState<OneTimeSecret | null>(null);

  const create = useCreateEndpoint((endpoint, signingSecret) => {
    setFormTarget(null);
    setSecret({ endpointUrl: endpoint.url, signingSecret });
  });
  const update = useUpdateEndpoint();
  const pause = usePauseEndpoint();
  const resume = useResumeEndpoint();
  const remove = useDeleteEndpoint();

  const fail = (title: string, e: unknown) =>
    toast.show({ tone: 'error', title, detail: describeError(e).detail });

  return {
    formTarget,
    pauseTarget,
    deleteTarget,
    secret,
    resumingId: resume.isPending ? resume.variables : null,
    deleting: remove.isPending,
    openCreate: () => setFormTarget({ mode: 'create' }),
    closeForm: () => setFormTarget(null),
    closePause: () => setPauseTarget(null),
    closeDelete: () => setDeleteTarget(null),
    /** Drops the secret: it only ever lived in this state. */
    closeSecret: () => setSecret(null),
    onAction(action: EndpointAction, e: Endpoint) {
      if (action === 'edit') setFormTarget({ mode: 'edit', endpoint: e });
      if (action === 'pause') setPauseTarget(e);
      if (action === 'delete') setDeleteTarget(e);
      if (action === 'resume') {
        resume.mutate(e.id, {
          onSuccess: () =>
            toast.show({
              tone: 'success',
              title: 'Endpoint resumed',
              detail: 'Queued deliveries will be sent again.',
            }),
          onError: (err) => fail('Could not resume endpoint', err),
        });
      }
    },
    async submitForm(values: EndpointFormValues) {
      if (formTarget?.mode === 'edit') {
        const body = toUpdateRequest(formTarget.endpoint, values);
        if (Object.keys(body).length > 0)
          await update.mutateAsync({ id: formTarget.endpoint.id, body });
        setFormTarget(null);
        toast.show({ tone: 'success', title: 'Endpoint saved' });
      } else {
        await create.mutateAsync(toCreateRequest(values));
      }
    },
    async submitPause(reason: string) {
      if (!pauseTarget) return;
      await pause.mutateAsync({ id: pauseTarget.id, reason });
      setPauseTarget(null);
      toast.show({ tone: 'success', title: 'Endpoint paused' });
    },
    confirmDelete() {
      if (!deleteTarget) return;
      remove.mutate(deleteTarget.id, {
        onSuccess: () => {
          setDeleteTarget(null);
          toast.show({ tone: 'success', title: 'Endpoint deleted' });
        },
        onError: (err) => fail('Could not delete endpoint', err),
      });
    },
  };
}
