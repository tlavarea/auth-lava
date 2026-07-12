# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Coding conventions (TypeScript/Angular style, state management, testing rules, styling) live in `.claude/CLAUDE.md` and are loaded automatically — this file covers commands and architecture.

## Commands

Package manager is **pnpm** — never `npm`/`yarn`.

```bash
pnpm install                 # install deps
pnpm start                   # ng serve, dev server at localhost:4200, proxies /api, /oauth2, /login/oauth2 to localhost:8080
pnpm build                   # production build -> dist/
pnpm test                    # ng test (Vitest via @angular/build:unit-test)
pnpm lint                    # ng lint
pnpm lint:fix                # ng lint --fix
pnpm prettier:check          # check formatting of src/**/*.{ts,html,css,scss,json}
pnpm prettier:format         # write formatting
pnpm e2e                     # Playwright e2e tests (frontend/e2e/) — starts its own dev server, backend is mocked
pnpm e2e:ui                  # same, in Playwright's interactive UI mode
```

Run a single test file/suite with Vitest's CLI filtering, e.g.:

```bash
pnpm test -- src/app/core/auth/auth.store.spec.ts
pnpm test -- -t "some test name"
```

Prefer the **angular-cli MCP server** (`run_target`, `get_best_practices`, `list_projects`) over raw `ng`/shell invocations for build/test/serve and best-practices lookups.

There is one Angular CLI project, `auth-lava-web` (see `angular.json`), root at repo root, source root `src/`.

## Architecture

This is the web frontend for a Spring Boot-backed auth service ("auth lava"), proxied at `/api`, `/oauth2`, `/login/oauth2` (see `proxy.conf.json`). The backend issues cookie-based session credentials — the frontend never handles tokens directly.

### Auth flow (`src/app/core/auth/`)

- **`AuthStore`** (`auth.store.ts`) — the single source of truth for auth state, an `@ngrx/signals` `signalStore` (`providedIn: 'root'`). State is `{ status: 'unknown' | 'authenticated' | 'mfa-pending' | 'anonymous', user }`. All auth transitions (login, logout, registration, MFA enroll/verify) go through its methods, which call `AuthApi` and `patchState`. `bootstrap()` is invoked once via `provideAppInitializer` in `app.config.ts` to resolve initial auth state from the session cookie before the app renders.
- **`AuthApi`** (`auth-api.ts`) — thin `HttpClient` wrapper over `${environment.apiUrl}/api/auth/*` endpoints (register/start, verify-code, complete, login, me, refresh, logout, mfa/enroll, mfa/enroll/verify, mfa/verify). Uses the `@Service()` decorator (not `@Injectable({providedIn:'root'})`).
- **Route guards** (`src/app/core/guards/`) — `authGuard`, `guestGuard`, `mfaPendingGuard` all switch on `AuthStore.status()` and redirect via `UrlTree` (e.g. authenticated users hitting `/login` get redirected to `/`, unauthenticated users hitting protected routes get redirected to `/login`, `mfa-pending` users are funneled to `/mfa/verify`). Routes are wired to these in `app.routes.ts`.
- **HTTP interceptors** (`src/app/core/http/`), registered in `app.config.ts` in order `[credentialsInterceptor, authErrorInterceptor]`:
  - `credentialsInterceptor` — clones every request with `withCredentials: true` so the session cookie is sent.
  - `authErrorInterceptor` — on a 401 from a non-exempt path, calls `AuthApi.refresh()` and retries the original request once; if refresh also fails, calls `authStore.forceLogout()` and navigates to `/login`. `REFRESH_EXEMPT_PATHS` lists auth endpoints that must not trigger a refresh loop.
- **OAuth** (`oauth-providers/oauth-providers.ts`) — Google/GitHub sign-in is a plain redirect (`window.location.href = '/oauth2/authorization/{provider}'`), not an API call; the backend handles the OAuth2 dance server-side.
- **`extract-error-message.ts`** — pulls a user-facing message out of an `HttpErrorResponse` body (`{ error: string }`), with a generic fallback.

### Routing (`src/app/app.routes.ts`)

All feature routes are lazy-loaded (`loadComponent`). Route map: `/` (dashboard, `authGuard`), `/login` and `/register` (`guestGuard`), `/mfa/enroll` (`authGuard`), `/mfa/verify` (`mfaPendingGuard`), wildcard redirects to `/`.

### Feature pages (`src/app/features/`)

Each feature (`login`, `register`, `mfa-enroll`, `mfa-verify`, `dashboard`) is a single standalone routed page component (`*.page.ts`) with an inline template, using Signal Forms (`@angular/forms/signals`: `form()`, `FormField`, `FormRoot`) for anything with input validation, and injecting `AuthStore` directly rather than going through an intermediate service.

### UI library (`libs/ui/`)

Generated spartan/ui components — Helm-styled wrappers around `@spartan-ng/brain` primitives, one directory per component (e.g. `libs/ui/button`, `libs/ui/card`, `libs/ui/field`). Each is imported via the `@spartan-ng/helm/<name>` path alias (mapped in `tsconfig.json`, configured by `components.json`: `componentsPath: libs/ui`, `importAlias: @spartan-ng/helm`). These are ESLint-ignored (`eslint.config.js` excludes `libs/ui/**`) since they're generated code — add new components with the `@spartan-ng/cli` generator rather than hand-writing them, and don't hand-edit generated internals beyond what the spartan skill/generators support.

### Path aliases (`tsconfig.json`)

- `@core/*` → `src/app/core/*`
- `@features/*` → `src/app/features/*`
- `@env/*` → `src/environments/*`
- `@spartan-ng/helm/<component>` → `libs/ui/<component>/src/index.ts`

### End-to-end tests (`e2e/`)

Playwright drives the real app in a browser; the Spring Boot backend is never
required — `/api/auth/**` is mocked at the network layer, so `pnpm e2e` works
with the backend fully stopped.

- **`e2e/support/fake-auth-backend.ts`** — `FakeAuthBackend`, an in-memory
  stand-in for every `/api/auth/**` endpoint, keyed off the contract in
  `src/app/core/auth/auth-api.ts` / `auth.models.ts` (the actual source of
  truth for request/response shapes — not the Java backend). Holds mutable
  session state and exposes scenario setters (`withAuthenticatedUser`,
  `withMfaPendingUser`, `withRegisteredUser`, ...) that specs call before
  `page.goto(...)`.
- **`e2e/support/fixtures.ts`** — wraps `@playwright/test`'s `test`/`expect`
  with an `auto: true` `backend` fixture that installs the fake backend's
  `page.route('**/api/auth/**', ...)` handler before every test, whether or
  not the spec destructures `backend` — a spec that skips this fixture would
  otherwise leak real requests to the (usually absent) backend.
- **`e2e/specs/*.spec.ts`** — one file per user flow (login, register, MFA
  enroll/verify/disable, password/email change, route guards, OAuth).

Conventions for new specs:
- Prefer `getByRole`/`getByLabel` locators over CSS selectors or
  `data-testid` — the app already requires WCAG AA/AXE-passing markup (see
  `.claude/CLAUDE.md`), so accessible names are already there.
- Add new backend behavior to `FakeAuthBackend` rather than registering ad
  hoc `page.route` calls inside a spec, so every test shares one accurate
  mock of the API contract. Note that the frontend's `authErrorInterceptor`
  treats a bare `401` from most (non-exempt, see `REFRESH_EXEMPT_PATHS` in
  `auth-error-interceptor.ts`) endpoints as "session expired" and force-logs
  the user out — match the real backend's actual status codes for
  invalid-input-but-still-authenticated cases (e.g. a wrong MFA code), not
  just whatever seems intuitive, or specs will silently redirect to `/login`
  instead of exercising the inline error path.
- Consult current Playwright docs via the context7 MCP plugin
  (`/microsoft/playwright`) when using an unfamiliar API — its surface moves
  quickly enough that training data can be stale.
- The **Playwright MCP server** (`claude mcp add playwright npx @playwright/mcp@latest`)
  is available for interactively exploring the running app's accessibility
  tree while authoring/debugging specs (`pnpm start`, then navigate/snapshot
  through the MCP tools to confirm locators resolve before writing them into
  a spec). It's an authoring aid only — not used by `pnpm e2e` itself.

### CI (`.github/workflows/frontend-build.yml`)

On PRs and pushes to `main`: `pnpm install --frozen-lockfile` → `ng lint` → `ng build` → `ng test --watch=false` → `playwright install --with-deps chromium` → `pnpm e2e`. A pre-commit hook (Husky + lint-staged) runs `prettier --write` and `eslint --fix` on staged `*.{ts,js,html}` files.
