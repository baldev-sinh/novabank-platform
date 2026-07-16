# NovaBank Web

NovaBank Web is the Angular frontend for the NovaBank Platform, a production-grade digital banking application built using modern enterprise architecture and engineering practices.

The application provides the user interface for customer authentication, account management, payments, transactions, and other banking capabilities exposed by the NovaBank backend microservices.

---

## Technology Stack

- Angular 20
- TypeScript
- SCSS
- Angular Router
- ESLint
- Prettier
- RxJS

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

## Lint

Run ESLint:

```bash
npm run lint
```

---

## Format

Format the project using Prettier:

```bash
npm run format
```

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

NovaBank Web communicates with the NovaBank backend through REST APIs exposed by the platform microservices.

Current backend services include:

- Authentication Service

Additional services will be integrated as the platform evolves.

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
