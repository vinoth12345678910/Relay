import { Navigate, Outlet, useLocation } from 'react-router';
import { useSession } from '@/session/store';

/** Gate for every page that needs an API key. Remembers where the user was going (path only, no secrets). */
export function RequireSession() {
  const s = useSession();
  const location = useLocation();
  if (s === null) {
    return (
      <Navigate to="/connect" replace state={{ from: `${location.pathname}${location.search}` }} />
    );
  }
  return <Outlet />;
}
