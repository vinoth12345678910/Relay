import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation } from '@tanstack/react-query';
import { Navigate, useLocation, useNavigate } from 'react-router';
import { KeyRound } from 'lucide-react';
import { verifyApiKey } from '@/api/client';
import { describeError, isApiError } from '@/api/errors';
import { Logo, ThemeToggle } from '@/app/AppShell';
import { Notice } from '@/components/ui/Feedback';
import { session, useSession } from '@/session/store';
import { ConnectForm } from './ConnectForm';
import { CreateAppPanel } from './CreateAppPanel';
import { connectSchema, type ConnectInput, type ConnectValues } from './schema';

function safeReturnPath(state: unknown): string {
  const from = typeof state === 'object' && state !== null && 'from' in state ? state.from : null;
  // Only same-app relative paths; never an absolute or protocol-relative URL.
  return typeof from === 'string' && from.startsWith('/') && !from.startsWith('//') ? from : '/';
}

export function ConnectPage() {
  const current = useSession();
  const navigate = useNavigate();
  const location = useLocation();
  const [notice] = useState(() => session.takeNotice());
  const [formError, setFormError] = useState<string | null>(null);
  const form = useForm<ConnectInput, unknown, ConnectValues>({
    resolver: zodResolver(connectSchema),
    defaultValues: { baseUrl: '', apiKey: '', remember: false },
  });
  const returnTo = safeReturnPath(location.state);

  const connect = useMutation({
    mutationFn: (v: ConnectValues) => verifyApiKey({ apiKey: v.apiKey, baseUrl: v.baseUrl }),
    onMutate: () => setFormError(null),
    onSuccess: (_data, v) => {
      session.connect({ apiKey: v.apiKey, baseUrl: v.baseUrl, remember: v.remember });
      form.reset({ baseUrl: v.baseUrl, apiKey: '', remember: v.remember });
      navigate(returnTo, { replace: true });
    },
    onError: (e) => {
      if (isApiError(e, 401))
        form.setError('apiKey', {
          message: 'Relay rejected this API key. Check it belongs to this server.',
        });
      else {
        const d = describeError(e);
        setFormError(`${d.title}. ${d.detail}`);
      }
    },
  });

  if (current !== null && !connect.isPending) return <Navigate to={returnTo} replace />;

  return (
    <div className="flex min-h-dvh flex-col">
      <header className="flex h-14 items-center justify-between border-b border-border px-4 md:px-6">
        <Logo />
        <ThemeToggle />
      </header>
      <main
        id="main"
        className="mx-auto grid w-full max-w-4xl flex-1 gap-6 px-4 py-8 md:grid-cols-[1.2fr_1fr] md:px-6"
      >
        <section
          aria-labelledby="connect-title"
          className="rounded-xl border border-border bg-surface p-5 md:p-6"
        >
          <div className="mb-5 flex items-center gap-2">
            <KeyRound aria-hidden className="size-5 text-accent" />
            <h1 id="connect-title" className="text-lg font-semibold">
              Connect to Relay
            </h1>
          </div>
          {notice && (
            <Notice tone="warning" className="mb-4">
              <span role="alert">{notice}</span>
            </Notice>
          )}
          <ConnectForm
            form={form}
            onSubmit={(v) => connect.mutate(v)}
            pending={connect.isPending}
            formError={formError}
          />
        </section>
        <section
          aria-labelledby="create-title"
          className="rounded-xl border border-border bg-surface p-5 md:p-6"
        >
          <h2 id="create-title" className="text-base font-semibold">
            No key yet? Create an app
          </h2>
          <p className="mt-1 mb-5 text-sm text-muted">
            Needs the server&apos;s admin token. You get the new app&apos;s API key once.
          </p>
          <CreateAppPanel
            baseUrl={form.watch('baseUrl').trim().replace(/\/+$/, '')}
            onConnect={(apiKey) => {
              const v = form.getValues();
              session.connect({
                apiKey,
                baseUrl: v.baseUrl.trim().replace(/\/+$/, ''),
                remember: v.remember,
              });
              navigate('/', { replace: true });
            }}
          />
        </section>
      </main>
    </div>
  );
}
