import { usePageTitle } from '@/lib/usePageTitle';
import { Link } from 'react-router';
import { Send } from 'lucide-react';
import { OpenEventForm } from './OpenEventForm';
import { RecentEventsList } from './RecentEventsList';

export function EventsPage() {
  usePageTitle('Events');
  return (
    <div className="flex flex-col gap-5">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold tracking-tight">Events</h1>
          <p className="mt-1 text-sm text-muted">
            Relay&apos;s API cannot list events, only fetch one by id. Find ids on deliveries or
            after publishing.
          </p>
        </div>
        <Link
          to="/events/new"
          className="inline-flex h-9 items-center gap-2 rounded-md bg-accent px-3.5 text-sm font-medium text-accent-fg hover:bg-accent-hover"
        >
          <Send aria-hidden className="size-4" /> Publish event
        </Link>
      </div>
      <div className="grid gap-5 md:grid-cols-2">
        <section className="rounded-xl border border-border bg-surface p-4">
          <OpenEventForm />
        </section>
        <RecentEventsList />
      </div>
    </div>
  );
}
