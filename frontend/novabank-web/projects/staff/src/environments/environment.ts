export const environment = {
  production: false,

  api: {
    // Same origin as the app, as in the customer app (Decision AP): `ng serve` proxies each path
    // to its service (src/proxy.conf.json), and the staff container's nginx routes the same paths.
    baseUrl: '/api/v1',
  },
};
