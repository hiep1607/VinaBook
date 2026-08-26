export const environment = {
  production: true,
  // Production uses the same origin. The hosting layer must proxy /api to Spring Boot.
  // This prevents deployed browsers from accidentally calling their own localhost.
  apiUrl: '',
  // Official VinaBook social profile URLs. Fill ONLY real account URLs.
  // Empty array (default) hides the footer "Kết nối" section entirely.
  socialLinks: [] as Array<{ label: string; url: string }>
};
