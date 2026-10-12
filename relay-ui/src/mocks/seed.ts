import type { Delivery, DeliveryStatus } from '@/api/schemas';
import { createDb, db, nextId, nowIso, type Db, type StoredEndpoint } from './db';

/** Demo/test data: three endpoints (one paused by the breaker) and deliveries in every status. */
export function seedDb(): Db {
  const d = createDb();
  const ep = (url: string, filter: string, extra: Partial<StoredEndpoint> = {}): StoredEndpoint => {
    const ts = nowIso(d);
    const e: StoredEndpoint = {
      id: nextId(d),
      url,
      description: null,
      eventTypeFilter: filter,
      status: 'ACTIVE',
      consecutiveFailures: 0,
      pausedReason: null,
      createdAt: ts,
      updatedAt: ts,
      ...extra,
    };
    d.endpoints.push(e);
    return e;
  };
  const orders = ep('https://hooks.example.com/orders', 'order.*', {
    description: 'Order service',
  });
  const billing = ep('https://billing.example.net/relay', '*', {
    description: 'Billing',
    status: 'PAUSED',
    consecutiveFailures: 20,
    pausedReason: 'Circuit breaker opened after 20 consecutive failures',
  });
  ep('https://audit.example.org/in', 'user.created');

  const event = (type: string, payload: unknown) => {
    const e = {
      id: nextId(d),
      type,
      payloadJson: JSON.stringify(payload),
      createdAt: nowIso(d),
      idempotencyKey: null,
    };
    d.events.push(e);
    return e;
  };
  const delivery = (
    eventId: string,
    endpointId: string,
    status: DeliveryStatus,
    attempts: number,
    code: number | null,
  ) => {
    const ts = nowIso(d);
    const del: Delivery = {
      id: nextId(d),
      eventId,
      endpointId,
      status,
      attemptCount: attempts,
      attemptBase: 0,
      nextAttemptAt: ts,
      lastStatusCode: code,
      createdAt: ts,
      updatedAt: ts,
    };
    d.deliveries.push(del);
    d.attempts.set(
      del.id,
      Array.from({ length: attempts }, (_, i) => {
        const last = i === attempts - 1;
        const ok = last && status === 'SUCCEEDED';
        return {
          attemptNumber: i + 1,
          startedAt: ts,
          durationMs: 40 + i * 13,
          statusCode: ok ? 200 : (code ?? null),
          error: ok ? null : code === null ? 'connect timed out' : `HTTP ${code}`,
          responseSnippet: ok
            ? '{"ok":true}'
            : '<html><body><h1>502 Bad Gateway</h1><script>alert(1)</script></body></html>',
        };
      }),
    );
    return del;
  };

  const e1 = event('order.paid', { orderId: 42, amount: 1999, currency: 'EUR' });
  delivery(e1.id, orders.id, 'SUCCEEDED', 1, 200);
  delivery(e1.id, billing.id, 'DEAD', 9, 502);
  const e2 = event('order.refunded', { orderId: 41, amount: 500 });
  delivery(e2.id, orders.id, 'DEAD', 9, null);
  delivery(e2.id, billing.id, 'PENDING', 3, 503);
  const e3 = event('order.created', { orderId: 43 });
  delivery(e3.id, orders.id, 'IN_FLIGHT', 1, null);
  delivery(e3.id, billing.id, 'PENDING', 0, null);
  return d;
}

export function resetDb(seed = true) {
  db.current = seed ? seedDb() : createDb();
}
