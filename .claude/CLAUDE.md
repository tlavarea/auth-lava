You are an expert in TypeScript, Angular, and scalable web application development. You write functional, maintainable, performant, and accessible code following Angular and TypeScript best practices.

## Tooling

- Package manager: **pnpm**. Never use `npm` or `yarn` — always `pnpm install`, `pnpm add`, `pnpm exec`, etc.
- Use the **angular-developer** skill for Angular architecture, reactivity, forms, DI, routing, SSR, accessibility, styling, and testing guidance.
- Use the **angular-cli** MCP server (not raw shell commands) for CLI operations: discovering the workspace (`list_projects`), loading version-specific best practices (`get_best_practices`), running builds/tests/serve (`run_target`), and documentation lookups (`search_documentation`).
- Use the **spartan** skill for anything involving spartan/ui components (Brain headless primitives or Helm styled layer), and the **@spartan-ng/mcp** server for component APIs, generators, and usage examples.
- New UI components are generated via the `@spartan-ng/cli` (see `components.json`) into `libs/ui/<component>`, using the `@spartan-ng/helm` import alias.

## TypeScript Best Practices

- Use strict type checking
- Prefer type inference when the type is obvious
- Avoid the `any` type; use `unknown` when type is uncertain

## Angular Best Practices

- Always use standalone components over NgModules
- Must NOT set `standalone: true` inside Angular decorators. It's the default in Angular v20+.
- Do NOT set `changeDetection: ChangeDetectionStrategy.OnPush` explicitly. `OnPush` is the default in Angular v22+.
- Use signals for state management
- Implement lazy loading for feature routes
- Do NOT use the `@HostBinding` and `@HostListener` decorators. Put host bindings inside the `host` object of the `@Component` or `@Directive` decorator instead
- Use `NgOptimizedImage` for all static images.
  - `NgOptimizedImage` does not work for inline base64 images.

## Accessibility Requirements

- It MUST pass all AXE checks.
- It MUST follow all WCAG AA minimums, including focus management, color contrast, and ARIA attributes.

### Components

- Keep components small and focused on a single responsibility
- Use `input()` and `output()` functions instead of decorators
- Use `computed()` for derived state
- Prefer inline templates for small components
- Prefer Signal Forms (`@angular/forms/signals`) for new forms. They are stable in Angular v22+ and provide signal-based state, type-safe field access, and schema-based validation
- When not using Signal Forms, prefer Reactive forms instead of Template-driven ones
- Do NOT use `ngClass`, use `class` bindings instead
- Do NOT use `ngStyle`, use `style` bindings instead
- When using external templates/styles, use paths relative to the component TS file.

## Styling

- Use Tailwind CSS utility classes for styling; avoid hand-written CSS unless Tailwind can't express it.
- Prefer spartan/ui (`@spartan-ng/brain` + Helm components in `libs/ui`) over building custom UI primitives from scratch.
- Use `class-variance-authority` (`cva`) for variant-driven component styling, and `tailwind-merge`/`clsx` (via `libs/ui/utils`) for conditional/merged class composition, consistent with existing Helm components.

## State Management

- Use signals for local component state
- Use `computed()` for derived state
- Keep state transformations pure and predictable
- Do NOT use `mutate` on signals, use `update` or `set` instead
- For shared/feature-level state, use `@ngrx/signals` (SignalStore) rather than services with plain signals or NgRx Store/Effects.
  - Compose stores with `withState`, `withComputed`, `withMethods`, and feature slices (`withEntities`, etc.) instead of one monolithic store.
  - Keep `withMethods` updaters pure and use `patchState` rather than mutating state directly.
  - Provide stores with `providedIn: 'root'` for app-wide state, or at the route/component level for feature-scoped state.

## Templates

- Keep templates simple and avoid complex logic
- Use native control flow (`@if`, `@for`, `@switch`) instead of `*ngIf`, `*ngFor`, `*ngSwitch`
- Use the async pipe to handle observables
- Do not assume globals like (`new Date()`) are available.

## Services

- Design services around a single responsibility
- Use the `providedIn: 'root'` option for singleton services
- Prefer the `@Service` decorator over `@Injectable({providedIn: 'root'})` for new singleton services (Angular v22+)
- Use the `inject()` function instead of constructor injection

## Testing

- Unit tests run on **Vitest** via the Angular CLI (`@angular/build:unit-test`), invoked with `pnpm test` / `ng test` — do not add Jasmine/Karma or a separate Vitest config.
- Use Vitest globals (`describe`, `it`, `expect`, `vi`) as configured in `tsconfig.spec.json`; don't import them manually.
- Test SignalStores by reading their signals/computed properties directly rather than mocking internals.
