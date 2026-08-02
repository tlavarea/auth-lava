# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A repo for the `auth-lava` authentication service and the Angular SPA built on it:

- **`backend/`** — Spring Boot 4.1 / Java 25 auth service (email+password, OAuth2, JWT + refresh tokens, TOTP MFA). See `backend/CLAUDE.md` for its architecture and conventions.
- **`frontend/`** — Angular SPA that consumes auth-lava's cookie-based API, plus the dispatch screens served by `sw-expedited`. See `frontend/CLAUDE.md` (commands/architecture) and `frontend/.claude/CLAUDE.md` (Angular/TypeScript coding conventions, loaded automatically when working under `frontend/`).

**`sw-expedited`** — the Spring Boot service behind the dispatch screens (drivers, trucks, trailers, shipments, schedule) — lives in a **separate private repository** (`tlavarea/sw-expedited`) and is not part of this repo. It sits behind auth-lava rather than duplicating its login/session logic, trusting auth-lava's JWTs directly: it fetches auth-lava's public key from the JWKS endpoint (`/.well-known/jwks.json`) via Spring Security's OAuth2 resource server support, and reads the bearer token from the same `ACCESS_TOKEN` cookie auth-lava issues rather than an `Authorization` header.

auth-lava and the frontend talk over HTTP: the Angular dev server proxies `/api`, `/oauth2`, `/login/oauth2` to the Spring Boot app on `localhost:8080` (see `frontend/proxy.conf.json`), and the backend's CORS config defaults to allowing `http://localhost:4200`. Auth state is cookie-based (httpOnly JWT + opaque refresh token) — the frontend never handles tokens directly. `sw-expedited` runs on `localhost:8081`; the dev proxy forwards `/api/sw-expedited/**` there (rewritten to `/api/**`) so it lives on the same origin as auth-lava from the browser's perspective. Only auth-lava issues or refreshes tokens — `sw-expedited` (and any future service added the same way) only ever verifies them.

## Commands

- Shared dev infra (Postgres + Mailpit + an OTel collector/Tempo/Prometheus pipeline + Loki/Grafana at `localhost:3000`): `docker-compose up -d` from the repo root
- Backend: `cd backend && ./mvnw verify` (build + test), `./mvnw spring-boot:run` (run locally, needs a `.env` — see `backend/CLAUDE.md`)
- sw-expedited: lives in its own private repo (`tlavarea/sw-expedited`); see that repo's `CLAUDE.md` for its commands, required env vars, and integration setup. The dispatch screens in `frontend/` need it running on `localhost:8081`.
- Frontend: `cd frontend && pnpm install && pnpm start` (dev server at `localhost:4200`), `pnpm build`, `pnpm test`

For anything specific to one side (testing conventions, schema migrations, Angular state management, etc.), see that subdirectory's `CLAUDE.md`.

## Observability

`backend/` (and `sw-expedited`, from its own repo) exports logs, traces, and metrics over OTLP (`spring-boot-starter-opentelemetry`, configured entirely via the service's `management.opentelemetry.*`/`management.otlp.*` properties in `application.yaml` — no custom Logback appender or code) to `docker-compose`'s `otel-collector` service (`localhost:4317`/`4318`), rather than the more common container-log-collection setup — these apps run as host processes (`./mvnw spring-boot:run`), not Docker containers, so there's no container stdout for a log driver or Promtail to pick up. The collector fans out: logs to Loki (`docker/otel-collector/config.yaml`'s `otlphttp/loki` exporter, hitting Loki's native OTLP endpoint), traces to Tempo (native OTLP, no translation), and metrics to Prometheus (scraped from the collector's `prometheus` exporter). Query all three at `localhost:3000` (Grafana, anonymous admin access — this stack is dev-only, not for a shared/production deployment), provisioned as Loki/Tempo/Prometheus datasources with bidirectional trace↔log correlation wired via Tempo's `tracesToLogsV2` and Loki's `derivedFields` (`docker/grafana/provisioning/datasources/datasources.yaml`). If the collector isn't running, the OTel SDK's batch exporters drop data on export failure rather than blocking app startup or requests.

## CI

`.github/workflows/backend-build.yml` and `frontend-build.yml` are path-filtered (`backend/**` / `frontend/**`) so a change to one side doesn't trigger the other's build.

## Git hooks

There is a single pre-commit hook, managed by Husky and installed via `pnpm install` in `frontend/` (which sets `core.hooksPath`). `frontend/.husky/pre-commit` dispatches by staged path: `backend/**` changes run its own `hooks/pre-commit` (Maven Spotless check), `frontend/**` changes run `lint-staged` (Prettier + ESLint). Run `pnpm install` in `frontend/` at least once after cloning so the hook is registered.
