# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

`auth-lava` is a standalone Spring Boot 4.1 / Java 25 authentication service (package `com.lava`): email+password and OAuth2 (Google/GitHub) login, JWT access tokens + rotating opaque refresh tokens, email verification during registration, and TOTP-based MFA with backup codes. It's designed to sit behind an SPA (CORS defaults to `http://localhost:4200`, cookie-based tokens, Angular-style CSRF).

## Commands

- Build + run all tests (unit and Testcontainers integration tests): `./mvnw verify`
- Run a single test class: `./mvnw test -Dtest=AuthServiceImplTest`
- Run a single test method: `./mvnw test -Dtest=AuthServiceImplTest#login_returnsTokenPair`
- Run the app locally: `./mvnw spring-boot:run` (needs Postgres + Mailpit — `docker-compose up -d` starts both, and a `.env` with `POSTGRES_USER`/`POSTGRES_PASSWORD` plus the datasource/JWT/MFA env vars referenced in `application.yaml`)
- Format code (Palantir style via Spotless): `./mvnw spotless:apply` — runs automatically after every Edit/Write in this repo (see `.claude/settings.json` hook), and `spotless:check` runs via `hooks/pre-commit`, invoked by the monorepo's shared Husky pre-commit hook (`../frontend/.husky/pre-commit`) whenever a commit touches `backend/`, so don't hand-fix formatting
- jOOQ codegen (regenerates `com.lava.model.database` from the Liquibase changelog) runs automatically during `generate-sources`; it reads `src/main/resources/db/changelog/db.changelog-master.yaml`, not `schema.sql`

Coverage: JaCoCo enforces 80% line and 80% branch coverage bundle-wide on `verify` (excludes jOOQ-generated code, `*Builder` classes, and `Application`).

## Architecture

**Layering**: `controller` → `service` (interface + `*Impl`) → `repository` (interface + `*Impl` over jOOQ `DSLContext`) → jOOQ-generated tables under `com.lava.model.database` (generated into `target/generated-sources/jooq`, not checked in). Services are `@Transactional(readOnly = true)` at the class level with individual mutating methods overridden `@Transactional`. Follow this pattern for new services.

**Auth model**: Two tokens are issued together (`TokenPair`):
- **Access token**: short-lived signed JWT (`JwtServiceImpl`), carries the principal's authorities plus a `factors` claim array (one entry per satisfied auth factor — password, TOTP — with `authority` + `issuedAt`) and, when the user has MFA enrolled, an `MFA_ENROLLED` marker authority. Delivered as an httpOnly cookie via `AuthCookieFactory`.
- **Refresh token**: opaque high-entropy value; only its SHA-256 hash is stored (`refresh_token` table). Rotated on every use (`RefreshTokenService.rotate`); presenting an already-rotated-away token is treated as token theft and revokes all of that user's sessions. Also opaque-cookie delivered.

`JwtAuthenticationFilter` reconstructs `AuthUserPrincipal` (a Spring Security `UserDetails`) straight from JWT claims on every request — no DB hit — and adds `FactorGrantedAuthority` entries from the `factors` claim so Spring Security's built-in multi-factor `AuthorizationManagerFactory` can gate endpoints.

**MFA enforcement lives in `SecurityConfiguration`, not per-controller annotations.** It layers `AuthorizationManagerFactories.multiFactor()` on top of `authenticated()`, conditioned on the `MFA_ENROLLED` marker authority, so only users who actually enrolled TOTP are required to present the `TOTP_FACTOR_AUTHORITY` factor on `anyRequest()`. `/api/auth/mfa/verify` is deliberately excluded from that gate (it's the endpoint that upgrades a password-only token to carry the TOTP factor). See the extensive comments in `SecurityConfiguration.securityFilterChain` before changing this — the reasoning (why it's a local var not a `@Bean`, why `/error` must stay `permitAll`, why CSRF uses `spa()` verbatim) is non-obvious and previously caused subtle bugs.

**Registration is a three-step, email-verified flow**, coordinated across `RegistrationServiceImpl` and the `pending_registration` table:
1. `start(email)` — sends a numeric code via `EmailServiceImpl` (rate-limited by `registration.resend-cooldown`, attempt-limited by `registration.max-attempts`).
2. `verifyCode(email, code)` — hashes and compares (`Hasher`), marks the pending row verified, returns a short-lived signed "bridge" JWT (`generateRegistrationToken`, purpose-tagged so it can't be reused as an access token).
3. `complete(bridgeToken, password)` — re-checks the DB row is still `verified_at != null` (not just the token's own expiry) so a fresh `start()` call invalidates a stale bridge token, then creates the user.

**OAuth linking** (`OAuthAuthenticationServiceImpl`) only auto-links an OAuth identity to an existing user by email when the provider reports the email as verified — never link on an unverified provider email, that's an account-takeover vector. `GithubEmailBackfillOAuth2UserService` exists because GitHub's OAuth userinfo response omits the email by default.

**Config binding**: all tunables are `@ConfigurationProperties` records under `com.lava.boot.autoconfigure.app` (`JwtProperties`, `MfaProperties`, `RegistrationProperties`, `CorsProperties`, `CookieProperties`, `MailProperties`, `OAuthProperties`), bound from `application.yaml`, which is entirely env-var driven (`${VAR:default}`) — no profile-specific YAML for prod.

**Repository pattern**: each repository interface has a single `*Impl` injecting `DSLContext` directly (no Spring Data repositories). `UserRepositoryImpl` is the canonical example of composing a multi-table view (`AuthUserView`, roles + permissions flattened into sets) with a hand-written jOOQ join, plus the `email` case-insensitivity convention (always stored/queried lower-cased, not enforced via `CITEXT`).

**Generated code / record builders**: DTOs and jOOQ POJOs use `io.soabase.record-builder` (`XyzBuilder.builder()...build()` for records like `TokenPair`, `TotpEnrollment`, `AuthUserView`) rather than Lombok `@Builder` on records. Regular classes (`AuthUserPrincipal`) still use Lombok.

**Logging**: never log raw user input directly — wrap with `LogSanitizer.sanitize(...)` (OWASP-encodes for log injection) as done throughout the `service`/`controller` layers.

**Schema migrations**: Liquibase changesets live in `src/main/resources/db/changelog/changes/*.yaml`, referenced from `db.changelog-master.yaml` in order — add new changesets there, don't edit old ones. `schema.sql` at the resources root is a design reference/seed script, not the live migration path (Liquibase is what actually runs, per `spring.liquibase.change-log` in `application.yaml`).

## Testing

- Unit tests (`service`, `web`, `security` packages) mock collaborators directly — no Spring context.
- Repository tests (`src/test/java/com/lava/repository/*ImplTest`) extend `AbstractRepositoryIntegrationTest`, which boots a single shared `PostgreSQLContainer` (`PostgresTestContainer`, `postgres:18-alpine`) via `@ServiceConnection` and runs real Liquibase migrations against it — no mocking of jOOQ.
- `EmailServiceImplTest` similarly uses a shared `MailpitTestContainer` and asserts against real captured SMTP messages via `MailpitClient` (a hand-rolled REST client for Mailpit's HTTP API), not by mocking `JavaMailSender`.
- `src/test/resources/application-test.yaml` (`test` profile) supplies fixed dummy secrets — reuse these values rather than inventing new ones when writing new tests.
