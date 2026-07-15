# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A monorepo for the `auth-lava` authentication service and the services built behind it:

- **`backend/`** — Spring Boot 4.1 / Java 25 auth service (email+password, OAuth2, JWT + refresh tokens, TOTP MFA). See `backend/CLAUDE.md` for its architecture and conventions.
- **`sw-expedited/`** — Spring Boot 4.1 / Java 25 service for application functionality, sitting behind auth-lava rather than duplicating its login/session logic. It trusts auth-lava's JWTs directly: `SecurityConfiguration` fetches auth-lava's public key from its JWKS endpoint (`/.well-known/jwks.json`) via Spring Security's OAuth2 resource server support, and reads the bearer token from the same `ACCESS_TOKEN` cookie auth-lava issues (`CookieBearerTokenResolver`) rather than an `Authorization` header. Persists to its own `sw_expedited_db` database (a second database on the shared dev Postgres container) via Liquibase + jOOQ, mirroring `backend`'s stack.
- **`frontend/`** — Angular SPA that consumes both backends' cookie-based APIs. See `frontend/CLAUDE.md` (commands/architecture) and `frontend/.claude/CLAUDE.md` (Angular/TypeScript coding conventions, loaded automatically when working under `frontend/`).

auth-lava and the frontend talk over HTTP: the Angular dev server proxies `/api`, `/oauth2`, `/login/oauth2` to the Spring Boot app on `localhost:8080` (see `frontend/proxy.conf.json`), and the backend's CORS config defaults to allowing `http://localhost:4200`. Auth state is cookie-based (httpOnly JWT + opaque refresh token) — the frontend never handles tokens directly. `sw-expedited` runs on `localhost:8081`; the dev proxy forwards `/api/sw-expedited/**` there (rewritten to `/api/**`) so it lives on the same origin as auth-lava from the browser's perspective. Only auth-lava issues or refreshes tokens — `sw-expedited` (and any future service added the same way) only ever verifies them.

## Commands

- Shared dev infra (Postgres + Mailpit + Zipkin at `localhost:9411`): `docker-compose up -d` from the repo root
- Backend: `cd backend && ./mvnw verify` (build + test), `./mvnw spring-boot:run` (run locally, needs a `.env` — see `backend/CLAUDE.md`)
- sw-expedited: `cd sw-expedited && ./mvnw verify` (build + test), `./mvnw spring-boot:run` (run locally; needs auth-lava running on `localhost:8080` for JWKS verification, Postgres running (`docker-compose up -d` from the repo root) and the root `.env`'s `POSTGRES_USER`/`POSTGRES_PASSWORD`; the GFM shipment sync additionally needs `GFM_KEYSTORE_PATH`/`GFM_KEYSTORE_PASSWORD`/`GFM_TRUSTSTORE_PATH`/`GFM_TRUSTSTORE_PASSWORD` env vars for the cert-based GFM login, pointing at a PKCS12 client cert and JKS truststore kept outside the repo)
- Frontend: `cd frontend && pnpm install && pnpm start` (dev server at `localhost:4200`), `pnpm build`, `pnpm test`

For anything specific to one side (testing conventions, schema migrations, Angular state management, etc.), see that subdirectory's `CLAUDE.md`.

## CI

`.github/workflows/backend-build.yml`, `sw-expedited-build.yml`, and `frontend-build.yml` are path-filtered (`backend/**` / `sw-expedited/**` / `frontend/**`) so a change to one side doesn't trigger another's build.

## Git hooks

There is a single pre-commit hook, managed by Husky and installed via `pnpm install` in `frontend/` (which sets `core.hooksPath`). `frontend/.husky/pre-commit` dispatches by staged path: `backend/**` and `sw-expedited/**` changes each run their own `hooks/pre-commit` (Maven Spotless check), `frontend/**` changes run `lint-staged` (Prettier + ESLint). Run `pnpm install` in `frontend/` at least once after cloning so the hook is registered.
