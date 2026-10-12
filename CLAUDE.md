# Relay

Reliable webhook delivery service. Maven multi-module Java 21 / Spring Boot 4 backend plus a React UI.

- `relay-server/` — the service (REST API, delivery poller). Build/test: `./mvnw verify` (needs Docker for Testcontainers).
- `mock-receiver/` — a webhook receiver that misbehaves on command.
- `docker/` — compose files. `scripts/` — chaos demo, load test, create-app.
- `relay-ui/` — browser UI (not a Maven module). **Read [relay-ui/CLAUDE.md](relay-ui/CLAUDE.md) before UI work.**
- The Java code is the source of truth for the API contract; `README.md` and `docs/` can lag behind it.
- UI work must not modify `relay-server/`, `mock-receiver/`, `pom.xml` or `docker/` without explicit approval.
