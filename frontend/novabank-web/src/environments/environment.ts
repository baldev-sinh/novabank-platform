export const environment = {
  production: false,

  api: {
    // Same origin as the app (Decision AP): `ng serve` proxies each path to its service
    // (src/proxy.conf.json); a deployment's reverse proxy routes the same paths. No CORS, and the
    // SameSite=Strict refresh cookie is first-party.
    baseUrl: '/api/v1',
  },
};
