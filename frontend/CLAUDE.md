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

### CI (`.github/workflows/build.yml`)

On PRs and pushes to `main`: `pnpm install --frozen-lockfile` → `ng lint` → `ng build` → `ng test --watch=false`. A pre-commit hook (Husky + lint-staged) runs `prettier --write` and `eslint --fix` on staged `*.{ts,js,html}` files.
