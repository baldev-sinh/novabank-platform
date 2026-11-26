# @novabank/ui

The NovaBank design system (Decision AN), shared by the customer and staff apps as a library in
this workspace (Decision AS). Not published: the apps use its build in `dist/ui`.

```bash
npx ng build ui     # or npm run watch:ui while serving an app
npx ng test ui
```

Components, pipes and the `decimal` helpers are exported from `src/public-api.ts`; the global SCSS
in `styles/` ships with the build and each app loads it with `@use 'ui/styles';`. How to use each
component: the workspace README, "Design system".
