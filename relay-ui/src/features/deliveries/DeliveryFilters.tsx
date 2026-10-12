import { useId } from 'react';
import { FilterX } from 'lucide-react';
import { DELIVERY_STATUSES } from '@/api/schemas';
import { deliveryStatusLabel } from '@/components/StatusBadge';
import { Button } from '@/components/ui/Button';
import { inputClass } from '@/components/ui/Field';
import { useEndpoints } from '@/features/endpoints/queries';
import type { DeliveryFilterState } from './filters';

export function DeliveryFilters({
  filters,
  onChange,
  onClear,
}: {
  filters: DeliveryFilterState;
  onChange: (key: 'status' | 'endpointId', value: string) => void;
  onClear: () => void;
}) {
  const endpoints = useEndpoints();
  const statusId = useId();
  const endpointId = useId();
  const knownEndpoint = endpoints.data?.some((e) => e.id === filters.endpointId) ?? true;
  return (
    <div
      role="group"
      aria-label="Filter deliveries"
      className="flex flex-wrap items-end gap-3 rounded-lg border border-border bg-surface p-3"
    >
      <div className="flex min-w-[160px] flex-col gap-1">
        <label htmlFor={statusId} className="text-xs font-medium text-muted">
          Status
        </label>
        <select
          id={statusId}
          className={inputClass}
          value={filters.status ?? ''}
          onChange={(e) => onChange('status', e.target.value)}
        >
          <option value="">All statuses</option>
          {DELIVERY_STATUSES.map((s) => (
            <option key={s} value={s}>
              {deliveryStatusLabel(s)}
            </option>
          ))}
        </select>
      </div>
      <div className="flex min-w-[220px] flex-1 flex-col gap-1">
        <label htmlFor={endpointId} className="text-xs font-medium text-muted">
          Endpoint
        </label>
        <select
          id={endpointId}
          className={inputClass}
          value={filters.endpointId ?? ''}
          onChange={(e) => onChange('endpointId', e.target.value)}
        >
          <option value="">All endpoints</option>
          {filters.endpointId && !knownEndpoint && (
            <option value={filters.endpointId}>
              Deleted or unknown endpoint ({filters.endpointId.slice(0, 8)}…)
            </option>
          )}
          {endpoints.data?.map((e) => (
            <option key={e.id} value={e.id}>
              {e.description ? `${e.description} · ${e.url}` : e.url}
            </option>
          ))}
        </select>
      </div>
      {(filters.status || filters.endpointId) && (
        <Button variant="ghost" onClick={onClear}>
          <FilterX aria-hidden className="size-4" /> Clear filters
        </Button>
      )}
    </div>
  );
}
