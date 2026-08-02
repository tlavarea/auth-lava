# auth-lava

A monorepo pairing a **from-scratch authentication service** with the logistics-dispatch application built behind it. The auth service issues cookie-based, RS256-signed JWTs and publishes a JWKS endpoint; downstream services trust those tokens directly as OAuth2 resource servers, never duplicating login or session logic. The application layer integrates several real-world freight, TMS, and telematics systems — including some that expose only non-standard or federated interfaces, modeled directly at the protocol level.

> Personal project. The stack, integrations, and infrastructure are production-shaped, but this is a single-author codebase built to explore a realistic auth + microservice architecture end to end.

---

## Architecture

```mermaid
flowchart TD
    Browser["Angular SPA<br/>(frontend)"]

    subgraph Origin["Same origin (dev proxy)"]
        Auth["auth-lava<br/>Spring Boot · :8080<br/>issues + refreshes JWTs"]
        SW["sw-expedited<br/>Spring Boot · :8081<br/>verifies JWTs only"]
    end

    Browser -->|"httpOnly cookie<br/>(JWT + refresh)"| Auth
    Browser -->|"/api/sw-expedited/**"| SW
    SW -->|"fetch public key<br/>/.well-known/jwks.json"| Auth

    Auth --> AuthDB[("auth_lava_db")]
    SW --> SWDB[("sw_expedited_db")]

    SW -.->|freight| GFM["Freight system<br/>(SAML/mTLS)"]
    SW -.->|TMS| Vektor["TMS<br/>(gRPC-Web)"]
    SW -.->|telematics| Samsara["Samsara"]
    SW -.->|routing| Maps["Google Routes/Places"]

    Auth --> OTel["OTel Collector → Loki · Tempo · Prometheus → Grafana"]
    SW --> OTel
```

Only **auth-lava** mints or refreshes tokens. Every other service — `sw-expedited` today, anything added the same way tomorrow — is a pure OAuth2 resource server that fetches auth-lava's public key from its JWKS endpoint and reads the bearer token from the same `ACCESS_TOKEN` cookie auth-lava sets, rather than an `Authorization` header. The frontend never touches a raw token.

---

## The three components

### `backend/` — auth-lava
A standalone authentication service.

- **Email + password** registration with a verify-code flow, plus **OAuth2** social login (Google, GitHub) handled entirely server-side.
- **JWT access tokens + opaque refresh tokens**, delivered as **httpOnly cookies** — the SPA can't read them, closing off token-theft-via-XSS.
- **RS256 signing** with a published **JWKS** (`/.well-known/jwks.json`) so downstream services verify signatures without a shared secret.
- **TOTP MFA** (enroll / verify / disable) with backup codes.
- **Rate limiting** on login and MFA-verify, and **email-change** flows.
- Spring Boot 4.1 · Java 25 · Postgres via Liquibase + jOOQ.

### `sw-expedited/` — the application
An expedited-freight dispatch service that sits behind auth-lava and owns its own database. Screens for drivers, trucks, trailers, shipments, and a weekly schedule with live route maps. Where it gets interesting is the integrations:

- **Federated freight-management system** — integrated via a **SAML2 + mutual-TLS (client-certificate) federation handshake** rather than a simple credential exchange.
- **Third-party TMS** — integrated over its **gRPC-Web interface**, modeling the wire protocol and per-account request headers directly.
- **Samsara** — telematics sync for truck/trailer diagnostics, driver duty-status (HOS), and live locations, matched to TMS records by VIN.
- **Google Routes + Places APIs** — driving-distance matrices for pickup-viability matching and full route geometry for the schedule map, with a Places-based address-normalization fallback for gated/restricted-access waypoints the Routes API won't route to directly.
- Spring Batch 6 sync jobs on schedules; Spring Boot 4.1 · Java 25 · Postgres.

### `frontend/` — Angular SPA
Consumes both backends over the same cookie-based origin.

- **Angular 22** (standalone components, native control flow, zoneless-style signals throughout).
- **Signal-based state** with `@ngrx/signals` SignalStores; **Signal Forms** for validated input.
- **spartan/ui** (Brain headless primitives + Helm styled layer) on **Tailwind CSS v4**.
- Auth handled by an `AuthStore` single source of truth, route guards, and HTTP interceptors that transparently **refresh-and-retry on 401**.
- **Playwright** e2e suite that runs against a full in-memory fake of the auth API — no backend required.

---

## Cross-cutting

- **Observability** — both services export logs, traces, and metrics over **OTLP** to an OpenTelemetry Collector, which fans out to **Loki / Tempo / Prometheus**, all queryable in **Grafana** with bidirectional trace↔log correlation. No custom appender — it's all configuration.
- **CI** — path-filtered GitHub Actions so a frontend change doesn't trigger backend builds (and vice versa); lint + build + test + e2e on every PR.
- **Git hygiene** — a Husky pre-commit hook dispatches by staged path (Maven Spotless for JVM code, Prettier + ESLint for the frontend). `main` is protected: no force-push or deletion, changes land via PR.

---

## Tech stack

| Layer | Stack |
|-------|-------|
| Auth service | Spring Boot 4.1, Java 25, Spring Security (OAuth2 + resource server), Liquibase, jOOQ, Postgres |
| Application service | Spring Boot 4.1, Java 25, Spring Batch 6, Liquibase, jOOQ, Postgres |
| Frontend | Angular 22, TypeScript 6, @ngrx/signals, Signal Forms, spartan/ui, Tailwind CSS v4, Vitest, Playwright |
| Infra (dev) | Docker Compose: Postgres, Mailpit, OpenTelemetry Collector, Loki, Tempo, Prometheus, Grafana |

---

## Running it locally

**Prerequisites:** Docker, Java 25, Node + [pnpm](https://pnpm.io/).

```bash
# 1. Shared dev infra — Postgres, Mailpit, and the full OTel → Grafana pipeline
docker-compose up -d           # from the repo root

# 2. Auth service (needs a .env — see backend/CLAUDE.md)
cd backend && ./mvnw spring-boot:run          # → localhost:8080

# 3. Frontend
cd frontend && pnpm install && pnpm start     # → localhost:4200
```

The core auth experience — registration, login, OAuth, MFA — runs with just those three steps. The `sw-expedited` service is optional locally and only needed for the dispatch screens; its external integrations (GFM, Vektor, Samsara, Google Maps) require their own credentials and degrade gracefully when unset. See `sw-expedited/CLAUDE.md` for the full list.

Grafana is at **localhost:3000** (anonymous admin, dev-only) and Mailpit — which captures all outbound verification/MFA email — at **localhost:8025**.

### Common commands

```bash
# Backend / sw-expedited
./mvnw verify                  # build + test

# Frontend
pnpm build                     # production build
pnpm test                      # unit tests (Vitest)
pnpm e2e                       # Playwright e2e (backend fully mocked)
pnpm lint
```

---

## Repository layout

```
backend/         auth-lava — the authentication service
sw-expedited/    the dispatch application behind it
frontend/        Angular SPA consuming both
docker/          Compose service configs (OTel Collector, Grafana provisioning, …)
docker-compose.yaml
```

Each subdirectory has its own `CLAUDE.md` documenting architecture, conventions, and gotchas in depth.

---

## License

Copyright (c) 2026 tlavarea. **All rights reserved.** This repository is made
publicly viewable for portfolio and evaluation purposes only — you're welcome to
read the code, but no rights to use, copy, modify, or redistribute it are
granted. See [`LICENSE`](./LICENSE) for the full terms.
