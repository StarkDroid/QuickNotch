import adapter from '@sveltejs/adapter-static';
import { vitePreprocess } from '@sveltejs/vite-plugin-svelte';

/** @type {import('@sveltejs/kit').Config} */
const config = {
	preprocess: vitePreprocess(),
	kit: {
		// Static output: every route is prerendered to real HTML at build time
		// (crawlable + fast LCP) and then hydrated into an SPA in the browser.
		adapter: adapter({ pages: 'build', assets: 'build', precompress: true, strict: true }),
		prerender: {
			// Explicit so a page is never dropped silently if a link to it moves.
			entries: ['/', '/download', '/changelog', '/connect', '/oauth/callback'],
			handleHttpError: 'fail'
		},
		inlineStyleThreshold: 20480,
		alias: {
			$components: 'src/lib/components',
			$data: 'src/lib/data'
		}
	}
};

export default config;
