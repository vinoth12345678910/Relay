import { NavLink, Outlet } from 'react-router';
import { Inbox, Link2, LogOut, Monitor, Moon, Send, Sun, Waypoints } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { ErrorBoundary } from '@/components/ErrorBoundary';
import { cn } from '@/lib/cn';
import { useTheme } from '@/lib/theme';
import { maskKey, session, useSession } from '@/session/store';

const NAV = [
  { to: '/endpoints', label: 'Endpoints', icon: Link2, end: false },
  { to: '/events/new', label: 'Publish', icon: Send, end: false },
  { to: '/events', label: 'Events', icon: Inbox, end: true },
  { to: '/deliveries', label: 'Deliveries', icon: Waypoints, end: false },
];

export function ThemeToggle() {
  const { preference, cycle } = useTheme();
  const Icon = preference === 'dark' ? Moon : preference === 'light' ? Sun : Monitor;
  return (
    <Button
      variant="ghost"
      size="sm"
      onClick={cycle}
      aria-label={`Theme: ${preference}. Change theme`}
      title={`Theme: ${preference}`}
    >
      <Icon aria-hidden className="size-4" />
      <span className="hidden capitalize sm:inline">{preference}</span>
    </Button>
  );
}

export function Logo() {
  return (
    <span className="flex items-center gap-2 font-semibold tracking-tight">
      <svg aria-hidden viewBox="0 0 32 32" className="size-6">
        <rect width="32" height="32" rx="7" className="fill-fg" />
        <path
          d="M8 20h7l3-8 3 12 2-4h1"
          fill="none"
          className="stroke-accent"
          strokeWidth="2.5"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      </svg>
      Relay <span className="font-normal text-muted">Console</span>
    </span>
  );
}

export function AppShell() {
  const s = useSession();
  return (
    <div className="min-h-dvh md:grid md:grid-cols-[220px_1fr]">
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:fixed focus:top-2 focus:left-2 focus:z-50 focus:rounded-md focus:bg-surface focus:px-3 focus:py-2"
      >
        Skip to content
      </a>
      <aside className="border-b border-border bg-surface md:sticky md:top-0 md:h-dvh md:border-r md:border-b-0">
        <div className="flex h-14 items-center justify-between px-4">
          <Logo />
          <div className="md:hidden">
            <ThemeToggle />
          </div>
        </div>
        <nav aria-label="Main" className="flex gap-1 overflow-x-auto px-2 pb-2 md:flex-col md:pb-0">
          {NAV.map(({ to, label, icon: Icon, end }) => (
            <NavLink
              key={to}
              to={to}
              end={end}
              className={({ isActive }) =>
                cn(
                  'flex items-center gap-2 rounded-md px-3 py-2 text-sm whitespace-nowrap',
                  isActive
                    ? 'bg-surface-2 font-medium text-fg'
                    : 'text-muted hover:bg-surface-2 hover:text-fg',
                )
              }
            >
              <Icon aria-hidden className="size-4" />
              {label}
            </NavLink>
          ))}
        </nav>
      </aside>
      <div className="flex min-w-0 flex-col">
        <header className="flex h-14 items-center justify-between gap-3 border-b border-border px-4 md:px-6">
          <p className="min-w-0 truncate text-xs text-muted">
            {s && (
              <>
                <span className="hidden sm:inline">Connected to </span>
                <span className="font-mono text-fg">{s.baseUrl || 'this origin'}</span>
                <span className="hidden sm:inline"> with key </span>
                <span className="ml-1 font-mono text-fg sm:ml-0">{maskKey(s.apiKey)}</span>
              </>
            )}
          </p>
          <div className="flex shrink-0 items-center gap-1">
            <span className="hidden md:inline-flex">
              <ThemeToggle />
            </span>
            <Button variant="ghost" size="sm" onClick={() => session.disconnect()}>
              <LogOut aria-hidden className="size-4" /> Disconnect
            </Button>
          </div>
        </header>
        <main id="main" tabIndex={-1} className="mx-auto w-full max-w-6xl flex-1 px-4 py-6 md:px-6">
          <ErrorBoundary>
            <Outlet />
          </ErrorBoundary>
        </main>
      </div>
    </div>
  );
}
