# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A monorepo for the `auth-lava` authentication service:

- **`backend/`** — Spring Boot 4.1 / Java 25 auth service (email+password, OAuth2, JWT + refresh tokens, TOTP MFA). See `backend/CLAUDE.md` for its architecture and conventions.
- **`frontend/`** — Angular SPA that consumes the backend's cookie-based auth API. See `frontend/CLAUDE.md` (commands/architecture) and `frontend/.claude/CLAUDE.md` (Angular/TypeScript coding conventions, loaded automatically when working under `frontend/`).

The two talk over HTTP: the Angular dev server proxies `/api`, `/oauth2`, `/login/oauth2` to the Spring Boot app on `localhost:8080` (see `frontend/proxy.conf.json`), and the backend's CORS config defaults to allowing `http://localhost:4200`. Auth state is cookie-based (httpOnly JWT + opaque refresh token) — the frontend never handles tokens directly.

## Commands

- Shared dev infra (Postgres + Mailpit): `docker-compose up -d` from the repo root
- Backend: `cd backend && ./mvnw verify` (build + test), `./mvnw spring-boot:run` (run locally, needs a `.env` — see `backend/CLAUDE.md`)
- Frontend: `cd frontend && pnpm install && pnpm start` (dev server at `localhost:4200`), `pnpm build`, `pnpm test`

For anything specific to one side (testing conventions, schema migrations, Angular state management, etc.), see that subdirectory's `CLAUDE.md`.

## CI

`.github/workflows/backend-build.yml` and `frontend-build.yml` are path-filtered (`backend/**` / `frontend/**`) so a change to one side doesn't trigger the other's build.

## Git hooks

There is a single pre-commit hook, managed by Husky and installed via `pnpm install` in `frontend/` (which sets `core.hooksPath`). `frontend/.husky/pre-commit` dispatches by staged path: `backend/**` changes run `backend/hooks/pre-commit` (Maven Spotless check), `frontend/**` changes run `lint-staged` (Prettier + ESLint). Run `pnpm install` in `frontend/` at least once after cloning so the hook is registered.
