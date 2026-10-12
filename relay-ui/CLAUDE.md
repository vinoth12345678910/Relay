# relay-ui

Browser UI for **Relay**, a reliable webhook delivery service. Apps publish events; Relay fans each
event out to every subscribed endpoint as a _delivery_, signs it (HMAC, `whsec_` secret), retries with
backoff, and parks exhausted ones as `DEAD` for replay. The Java backend lives in `../relay-server`
and is the source of truth for the API. This UI is a developer/demo console, not a public product.

## API contract (summary — code in `../relay-server` wins)

- Auth: `Authorization: Bearer <apiKey>` on all `/api/v1/**`; `POST /api/v1/apps` uses the admin token in the **same** header.
- Errors: `{timestamp,status,error,code,message,path,fieldErrors?}`; `fieldErrors` omitted when empty.
- Endpoints: `POST/GET /endpoints`, `GET/PATCH/DELETE /endpoints/{id}`, `POST /endpoints/{id}/pause|resume`.
  `signingSecret` only in the 201 create response. Never send `eventTypeFilter: ""` (400) — omit it.
- Events: `POST /events` → 202 `{id,type,createdAt,deliveryCount,replayed}`, optional `Idempotency-Key`,
  `Idempotent-Replayed: true` header on replay, 409 key reuse w/ other payload, 413 > 256 KB. `GET /events/{id}`.
- Deliveries: `GET /deliveries?status&endpointId&cursor&limit` → `{items,nextCursor}`; `/{id}`, `/{id}/attempts`,
  `POST /{id}/replay` → 202 `{delivery,message}`, 409 unless DEAD/SUCCEEDED.
- No list-events, no stats, no `/apps/me`, no secret rotation. Do not invent them.

## Commands (run in `relay-ui/`)

|                                   |                                                           |
| --------------------------------- | --------------------------------------------------------- |
| `npm run dev`                     | Vite on :5173, proxies `/api` → `http://localhost:8080`   |
| `npm run dev:mock`                | Same UI against the in-browser MSW mock backend           |
| `npm run build`                   | Typecheck + production build to `dist/`                   |
| `npm run lint` / `npm run format` | ESLint / Prettier                                         |
| `npm run typecheck`               | `tsc --noEmit`                                            |
| `npm test`                        | Vitest (unit, component, MSW integration, contract drift) |
| `npm run e2e`                     | Playwright against the mock-mode app                      |

## Architecture

```
src/
  api/        zod schemas (single source of DTO truth), http wrapper, per-resource clients, error mapping
  session/    in-memory credential store (+ opt-in sessionStorage), 401 expiry
  features/   connect/ endpoints/ events/ deliveries/ overview/  -> queries.ts (TanStack) + components + pages
  components/ ui/ primitives (Radix-based) and layout
  lib/        clock, time formatting, bytes, theme, cn
  mocks/      in-memory db + MSW handlers (validated through the same zod schemas)
e2e/          Playwright specs
```

## Conventions

- Server state only in TanStack Query; forms via react-hook-form + zod; parse every response with zod.
- Components < 200 lines, no business logic in components (put it in hooks / pure functions).
- Time comes from `useClock()` / `lib/clock`, never `Date.now()` inside a component.
- Mocks live only in `src/mocks`; they must return data that passes the real schemas.
- See `../.claude/rules/frontend.md` for the full rule set.

## Never do

- Never put API keys, admin tokens or signing secrets in URLs, logs, localStorage, toasts, error reports or test snapshots.
- Never persist the API key anywhere but memory, or sessionStorage when the user opts in. Never persist the admin token.
- Never use `dangerouslySetInnerHTML` or render server strings as HTML (ESLint enforces).
- Never retry a 4xx automatically; on 401 wipe the key and return to Connect.
- Never add page-number pagination; the API is cursor-only.
- Never modify `../relay-server`, `../mock-receiver`, `../pom.xml`, `../docker` from UI work.
- Never add a dependency without a one-line justification in the commit message.
