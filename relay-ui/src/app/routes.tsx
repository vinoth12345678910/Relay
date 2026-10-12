import { Navigate, Route, Routes } from 'react-router';
import { ConnectPage } from '@/features/connect/ConnectPage';
import { EndpointsPage } from '@/features/endpoints/EndpointsPage';
import { EventDetailPage } from '@/features/events/EventDetailPage';
import { EventsPage } from '@/features/events/EventsPage';
import { PublishPage } from '@/features/events/PublishPage';
import { AppShell } from './AppShell';
import { RequireSession } from './RequireSession';

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/connect" element={<ConnectPage />} />
      <Route element={<RequireSession />}>
        <Route element={<AppShell />}>
          <Route index element={<Navigate to="/endpoints" replace />} />
          <Route path="endpoints" element={<EndpointsPage />} />
          <Route path="events" element={<EventsPage />} />
          <Route path="events/new" element={<PublishPage />} />
          <Route path="events/:id" element={<EventDetailPage />} />
          <Route path="*" element={<NotFound />} />
        </Route>
      </Route>
    </Routes>
  );
}

function NotFound() {
  return (
    <div className="py-16 text-center">
      <h1 className="text-lg font-semibold">Page not found</h1>
      <p className="mt-1 text-sm text-muted">Use the navigation to get back on track.</p>
    </div>
  );
}
