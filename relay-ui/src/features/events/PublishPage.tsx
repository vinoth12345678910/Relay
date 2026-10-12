import { usePageTitle } from '@/lib/usePageTitle';
import { PublishForm } from './PublishForm';
import { PublishResult } from './PublishResult';
import { OpenEventForm } from './OpenEventForm';
import { RecentEventsList } from './RecentEventsList';
import { usePublishEvent } from './queries';

const newIdempotencyKey = () => crypto.randomUUID();

export function PublishPage() {
  usePageTitle('Publish event');
  const publish = usePublishEvent();
  return (
    <div className="flex flex-col gap-5">
      <div>
        <h1 className="text-xl font-semibold tracking-tight">Publish an event</h1>
        <p className="mt-1 text-sm text-muted">
          Relay stores it and fans it out to every matching endpoint, asynchronously.
        </p>
      </div>
      <div className="grid gap-5 lg:grid-cols-[1fr_340px]">
        <div className="flex flex-col gap-5">
          <section className="rounded-xl border border-border bg-surface p-4 md:p-5">
            <PublishForm onPublish={(v) => publish.mutateAsync(v)} newKey={newIdempotencyKey} />
          </section>
          {publish.data && publish.variables && (
            <PublishResult
              key={`${publish.data.id}-${publish.submittedAt}`}
              result={publish.data}
              submittedType={publish.variables.type}
            />
          )}
        </div>
        <aside className="flex flex-col gap-5">
          <section className="rounded-xl border border-border bg-surface p-4">
            <OpenEventForm />
          </section>
          <RecentEventsList />
        </aside>
      </div>
    </div>
  );
}
