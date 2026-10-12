# Relay Console (relay-ui)

A browser console for [Relay](../README.md): connect with an app's API key, manage endpoints, publish
events, browse deliveries with their attempt history, and replay dead ones.

React 18 · TypeScript (strict) · Vite · React Router · TanStack Query · Tailwind CSS · Radix ·
react-hook-form + zod · Vitest + Testing Library + MSW · Playwright + axe.

> **Security trade-off, stated plainly.** This console holds a long-lived Relay API key in the
> browser (in memory, or in `sessionStorage` if you tick "Remember on this device"). That is fine for
> local development and demos. It is **not** how a production dashboard should work: anything that
> runs JavaScript on the page (a malicious extension, an XSS bug) can use the key. For real use, put a
> thin backend-for-frontend in front of Relay that keeps the key server-side and gives the browser a
> short-lived session cookie instead.

## Requirements

- Node **20.19+** (CI uses 20, also tested on 22)
- npm **11** (`npm i -g npm@11`). npm 10 crashes while resolving Vitest 4's peer dependencies;
  `package.json` pins `packageManager: npm@11.6.2` and `.npmrc` sets `engine-strict`.

```bash
cd relay-ui
npm ci
```

## Run against the real backend

```bash
# 1. Start Relay (from the repo root), e.g. with Docker:
docker compose -f docker/docker-compose.yml up --build -d
# 2. Start the UI:
cd relay-ui && npm run dev            # http://localhost:5173
```

The Vite dev server proxies `/api` to `http://localhost:8080`, so the browser talks to one origin and
the backend needs no CORS change. Leave **API base URL** empty on the Connect screen.

Get an API key with `scripts/create-app.sh my-app`, or use **Create an app** on the Connect screen
with the admin token (`dev-admin-token` in the dev profile). The new key is shown once.

Endpoints that point at other containers (like `http://mock-receiver:9000/hook/ok`) need
`RELAY_SSRF_ALLOW_PRIVATE_NETWORKS=true`. The compose stack sets it (`docker/relay.dev.env`); a
backend started another way rejects private addresses, and the UI shows that error on the URL field.

## Run against mocks (no backend)

```bash
npm run dev:mock                      # MSW in the browser, seeded demo data
```

Any key starting with `rl_live_` connects (one containing `revoked` gets a 401). The admin token is
`dev-admin-token`. The mock lives in `src/mocks` and validates every response against the same zod
schemas the client uses.

## Scripts

| Script              | What it does                                                                              |
| ------------------- | ----------------------------------------------------------------------------------------- |
| `npm run dev`       | Dev server on :5173, `/api` → `RELAY_API_TARGET` (default `http://localhost:8080`)        |
| `npm run dev:mock`  | Dev server with the in-browser mock backend                                               |
| `npm run typecheck` | `tsc --noEmit`                                                                            |
| `npm run lint`      | ESLint (incl. jsx-a11y, no `dangerouslySetInnerHTML`, no `localStorage`) + Prettier check |
| `npm test`          | Vitest: unit, component, MSW integration, API contract drift check                        |
| `npm run build`     | Typecheck + production build to `dist/` (mock code is not bundled)                        |
| `npm run e2e`       | Builds the mock-mode app and runs Playwright (flows, axe light/dark, 375px, CSP)          |
| `npm run format`    | Prettier write                                                                            |

## Environment variables

| Variable             | Where           | Default                    | Meaning                                                                    |
| -------------------- | --------------- | -------------------------- | -------------------------------------------------------------------------- |
| `RELAY_API_TARGET`   | dev server only | `http://localhost:8080`    | Where `/api` is proxied. Never reaches the bundle.                         |
| `VITE_USE_MOCKS`     | build time      | unset                      | `true` (set by `--mode mock`, see `.env.mock`) starts the MSW mock backend |
| `RELAY_API_UPSTREAM` | Docker image    | `http://relay-server:8080` | Where nginx proxies `/api`                                                 |

There are no runtime secrets in the build. The API key is entered by the user and never written to
`localStorage`, URLs, logs or analytics (tests assert this). The only `localStorage` entry is the
theme preference (`relay-ui.theme`).

## How the UI stays in sync with the API

`src/api/schemas.ts` is written by hand from the Java records. `src/api/contract.test.ts` parses
`../relay-server/src/main/java/.../*.java` and fails if a record component, enum constant, validation
regex, size limit or header name drifts. The CI workflow (`.github/workflows/ui.yml`) also runs when
backend Java sources change, for that reason.

Every response is parsed with zod; a shape the UI does not understand shows "Unexpected response from
Relay" instead of a blank screen.

## Docker (optional)

`Dockerfile` builds the app and serves it with nginx, which also proxies `/api` and sets a strict
CSP (`security-headers.inc`; e2e runs under the same policy and fails on violations).

```bash
docker build -t relay-ui relay-ui
```

It is **not** wired into `docker/docker-compose.yml`. To add it yourself:

```yaml
relay-ui:
  build: { context: ../relay-ui }
  environment:
    RELAY_API_UPSTREAM: http://relay-server:8080
  depends_on: [relay-server]
  ports: ['8081:8080']
```

## What the API cannot do (and how the UI copes)

- **No list-events endpoint.** Events open by id (from deliveries, the publish result, or the
  "published from this tab" list, which lives in memory only).
- **No stats endpoint.** The overview counts the newest 200 deliveries per status and shows `200+`
  when there are more; queue health is computed from that sample and says so.
- **No `/apps/me`.** The UI cannot show the connected app's name; it shows the key's first 12 chars.
- **No secret rotation.** A lost signing secret means deleting and re-adding the endpoint.
- Idempotency compares the **payload only**: re-sending a key with the same payload but a different
  type returns the original event. The UI warns when that happens.

## Layout

```
src/
  api/        schemas (zod), http wrapper, typed client, error mapping, contract test
  session/    credential store (memory, opt-in sessionStorage)
  features/   connect · endpoints · events · deliveries · overview
  components/ ui primitives (Radix-based), status badges, error boundary
  lib/        clock, time, bytes, json, theme, form error mapping
  mocks/      MSW handlers + in-memory db + seed
e2e/          Playwright specs
```
