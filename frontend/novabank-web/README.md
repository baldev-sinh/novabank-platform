# NovaBank Web

NovaBank Web is the Angular frontend for the NovaBank Platform, a production-grade digital banking application built using modern enterprise architecture and engineering practices.

The application provides the user interface for customer authentication, account management, payments, transactions, and other banking capabilities exposed by the NovaBank backend microservices.

---

## Technology Stack

- Angular 22 (standalone components, `@angular/build` application builder)
- TypeScript 6.0
- ESLint (angular-eslint) and Prettier
- SCSS with design tokens (CSS custom properties), our own components on Angular CDK 22
- Inter, self-hosted from `@fontsource-variable/inter` (no font CDN)
- Angular Router
- RxJS
- Vitest with jsdom for unit tests (`npx ng test --watch=false`)

Node.js: a version Angular 22 supports (`^22.22.3 || ^24.15.0 || ^26.0.0`); CI uses Node 26 LTS.

---

## Project Structure

```
src
├── app
│   ├── core            # Singleton services, guards, interceptors
│   ├── features        # Feature modules
│   ├── shared          # Shared components, directives, pipes
│   ├── layout          # Application layouts
│   ├── routing         # Application routing
│   └── app.config.ts
│
├── assets
├── environments
└── styles
```

---

## Development

### Install dependencies

```bash
npm install
```

### Run the application

```bash
npm start
```

The application will be available at:

```
http://localhost:4200
```

---

## Build

Create a production build:

```bash
npm run build
```

Build artifacts are generated in the `dist/` directory.

---

## Lint and format

```bash
npm run lint           # angular-eslint: TypeScript, Angular and template accessibility rules
npm run format         # Prettier rewrites files
npm run format:check   # what CI runs: fails on any file Prettier would change
```

ESLint (`eslint.config.js`, flat config from `ng add angular-eslint`) checks code; Prettier
(`.prettierrc.json`) owns formatting, so the two never disagree. CI runs both before the build.

---

## Design system

The look is defined once (roadmap 8.2, Decision AN; approved mockup:
https://claude.ai/artifact/JSA2BZ7U6fWVTm5d2D9DvS):

- `src/styles/_tokens.scss`: colours, type, spacing, radii, focus. Components use only these
  custom properties. Every text colour pair meets WCAG 2.2 AA; control outlines 3:1.
- `src/styles/_base.scss`, `_forms.scss`: page basics, focus rings, reduced motion, form controls.
- `src/app/shared/ui`: import from `shared/ui` (its `index.ts`).

| Use | Markup |
|---|---|
| Button | `<button nbButton>`, `<a nbButton="secondary">`; variants `primary`, `secondary`, `neutral`, `destructive`, `link`; `size="sm"`, `block`, `[busy]` (stays focusable, blocks a second submit) |
| Field | `<nb-field label="Amount" prefix="₹" hint="…" [error]="…"><input nbInput /></nb-field>`: label, hint and error wired to the control (`for`, `aria-describedby`, `aria-invalid`) |
| Alert | `<nb-alert tone="danger" title="Transfer not completed.">…</nb-alert>`: `danger` is `role="alert"`, others `role="status"` |
| Badge | `<nb-badge tone="success">Completed</nb-badge>` |
| Card | `<nb-card padding="md">`, `padding="none"` for edge-to-edge tables |
| Money | `amount \| nbMoney: currency : direction`: exact decimal strings, Indian grouping, `+`/`−` for money in/out |
| Account number | `number \| nbAccountNumber` (groups of 4) or `: 'last4'` |

---

## Architecture

The application follows a feature-based architecture with clear separation of responsibilities.

- **Core**
  - Singleton services
  - HTTP interceptors
  - Route guards
  - Global configuration

- **Features**
  - Banking business functionality
  - Lazy-loaded feature areas
  - Feature-specific services and components

- **Shared**
  - Reusable UI components
  - Pipes
  - Directives
  - Utility classes

- **Layout**
  - Application shell
  - Navigation
  - Header
  - Footer

---

## Backend Integration

The app calls the API on its own origin, under `/api/v1` (Decision AP). `npm start` proxies each
path to its service (`src/proxy.conf.json`), so start the backend first (repository README, "Whole
stack in containers"):

| Path | Service |
|---|---|
| `/api/v1/auth`, `/api/v1/users` | auth, `localhost:8081` |
| `/api/v1/customers` | customer, `localhost:8082` |
| `/api/v1/accounts` | account, `localhost:8083` |
| `/api/v1/payments` | payment, `localhost:8085` |

A deployment serves the same paths from a reverse proxy in front of the app. Same origin means no
CORS, and the refresh token cookie (`HttpOnly`, `SameSite=Strict`, path `/api/v1/auth`) is
first-party. The access token stays in memory and is only ever sent to `/api/v1` URLs.

Errors are read as RFC 9457 Problem Details (`core/http/problem.ts`): pages branch on `type` and
`status`, never on message text, and quote the `traceId` when something unexpected happens.

---

## Coding Standards

This project follows the NovaBank engineering standards:

- Angular Style Guide
- Strict TypeScript configuration
- ESLint for static analysis
- Prettier for code formatting
- Feature-based folder structure
- Standalone Angular components
- Reactive programming using RxJS

---

## Roadmap

Planned features include:

- User Authentication
- JWT Authentication
- Dashboard
- Customer Management
- Account Management
- Transactions
- Payments
- Beneficiaries
- Notifications
- User Profile
- Settings

---

## License

Internal NovaBank project.
