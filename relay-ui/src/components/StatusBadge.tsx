import {
  CircleCheck,
  CirclePause,
  CircleX,
  Clock,
  Send,
  Activity,
  Ban,
  type LucideIcon,
} from 'lucide-react';
import type { DeliveryStatus, EndpointStatus } from '@/api/schemas';
import { cn } from '@/lib/cn';

type Tone = 'success' | 'danger' | 'warning' | 'info' | 'neutral';
const TONES: Record<Tone, string> = {
  success: 'bg-success-bg text-success border-success/30',
  danger: 'bg-danger-bg text-danger border-danger/30',
  warning: 'bg-warning-bg text-warning border-warning/30',
  info: 'bg-info-bg text-info border-info/30',
  neutral: 'bg-surface-2 text-muted border-border',
};

/** Status is shown as colour + icon + text, never colour alone. */
const DELIVERY: Record<DeliveryStatus, { tone: Tone; icon: LucideIcon; label: string }> = {
  PENDING: { tone: 'warning', icon: Clock, label: 'Pending' },
  IN_FLIGHT: { tone: 'info', icon: Send, label: 'In flight' },
  SUCCEEDED: { tone: 'success', icon: CircleCheck, label: 'Succeeded' },
  DEAD: { tone: 'danger', icon: CircleX, label: 'Dead' },
};
const ENDPOINT: Record<EndpointStatus, { tone: Tone; icon: LucideIcon; label: string }> = {
  ACTIVE: { tone: 'success', icon: Activity, label: 'Active' },
  PAUSED: { tone: 'warning', icon: CirclePause, label: 'Paused' },
  DISABLED: { tone: 'neutral', icon: Ban, label: 'Deleted' },
};

function Badge({ tone, icon: Icon, label }: { tone: Tone; icon: LucideIcon; label: string }) {
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-xs font-medium whitespace-nowrap',
        TONES[tone],
      )}
    >
      <Icon aria-hidden className="size-3.5" />
      {label}
    </span>
  );
}

export function DeliveryStatusBadge({ status }: { status: DeliveryStatus }) {
  return <Badge {...DELIVERY[status]} />;
}

export function EndpointStatusBadge({ status }: { status: EndpointStatus }) {
  return <Badge {...ENDPOINT[status]} />;
}

export const deliveryStatusLabel = (s: DeliveryStatus) => DELIVERY[s].label;
