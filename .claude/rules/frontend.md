---
paths:
  - "relay-ui/**"
---

# Frontend rules (relay-ui)

## TypeScript
- `strict` is on and stays on. No `any` (explicit or implicit), no `@ts-ignore`; use `unknown` + zod narrowing.
- Types for API data come from `z.infer<typeof Schema>` in `src/api/schemas.ts`. Do not hand-write duplicate DTO types.
- Prefer `type` aliases and discriminated unions over enums.

## Components
- Under 200 lines per component file. Split by responsibility, not by size alone.
- No business logic in components: request building, status rules, size maths, URL <-> filter mapping live in
  hooks or pure functions under `features/*` or `lib/`, and get their own unit tests.
- No `Date.now()` / `new Date()` / `Math.random()` inside render; use `useClock()` and inject ids/time.

## State
- Server state only in TanStack Query (`features/*/queries.ts`). No copying query data into `useState`.
- Filters that define a view live in the URL query string.
- Credentials live only in `src/session`. Secrets returned once (signing secret, new API key) live in the
  modal's local state and are dropped when it closes.

## Forms
- react-hook-form + zod resolver. Validation rules mirror the backend regexes exactly.
- Backend `fieldErrors` are mapped onto form fields; other errors show as a readable form-level message.

## Errors
- Every page has loading, empty, error and success states.
- Never show raw JSON or stack traces. Use `describeError()` from `src/api/errors.ts`.

## Security
- No `dangerouslySetInnerHTML`, no `innerHTML`, no `eval`. Server strings render as React text.
- No `localStorage` except `src/lib/theme.ts` (theme key only). No logging of headers or bodies.

## Accessibility
- Every interactive element is reachable and operable by keyboard; visible focus ring.
- Every input has a `<label>`; errors are linked with `aria-describedby` and announced.
- Status is never colour-only: pair colour with an icon and text.
- Dialogs/drawers trap focus and return it on close (Radix). Toasts use a polite live region.
- Meet WCAG AA contrast in both themes; axe serious/critical issues are build-blocking in e2e.
