---
description: Run every relay-ui gate (typecheck, lint, unit tests, build, e2e) and print a pass/fail table
allowed-tools: Bash(npm run *), Bash(npm ci), Bash(cd *)
---

Run each step below **in `relay-ui/`**, in order, even if an earlier one fails. Capture the exit code and,
for failures, the last ~30 lines of output.

1. `npm run typecheck`
2. `npm run lint`
3. `npm test -- --run`
4. `npm run build`
5. `npm run e2e`

If `node_modules` is missing, run `npm ci` first.

Then print exactly one markdown table:

| Step | Result | Notes |
|---|---|---|
| typecheck | ✅ pass / ❌ fail | error count or "-" |
| lint | ... | |
| unit tests | ... | `N passed / M failed` |
| build | ... | bundle size of the main JS chunk |
| e2e | ... | `N passed / M failed` |

Finish with `ALL GREEN` or `RED: <failing steps>`. For each failure, show the relevant output excerpt below the table.
Do not fix anything unless asked.
