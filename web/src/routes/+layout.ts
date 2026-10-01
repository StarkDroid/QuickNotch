// Fully static output: SvelteKit prerenders the page to real HTML at build time
// (so crawlers and no-JS visitors get complete content) and then hydrates it into
// a client-side SPA for instant, app-like navigation.
export const prerender = true;
export const trailingSlash = 'never';
