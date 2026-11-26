import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';

import { routes } from './app.routes';

// Zoneless, the default for new Angular apps (no zone.js polyfill in angular.json): change
// detection runs on signals, inputs and events. https://angular.dev/guide/zoneless
export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    // Route parameters arrive as component inputs.
    provideRouter(routes, withComponentInputBinding()),
  ],
};
