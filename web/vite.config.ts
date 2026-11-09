import path from 'node:path';
import adapter from '@sveltejs/adapter-static';
import { sveltekit } from '@sveltejs/kit/vite';
import { vitePreprocess } from '@sveltejs/vite-plugin-svelte';
import { defineConfig } from 'vite';

export default defineConfig({
	plugins: [
		sveltekit({
			preprocess: vitePreprocess(),
			// Static output: every route is prerendered to real HTML at build time
			// (crawlable + fast LCP) and then hydrated into an SPA in the browser.
			adapter: adapter({ pages: 'build', assets: 'build', precompress: true, strict: true }),
			prerender: {
				// Explicit so a page is never dropped silently if a link to it moves.
				// `/404` is not a destination: it is rendered so the adapter can write
				// `404.html` at the root of the build, which the Worker serves for an
				// address that matches no route.
				entries: [
					'/',
					'/download',
					'/changelog',
					'/privacy',
					'/terms',
					'/connect',
					'/oauth/callback',
					'/404',
					'/sitemap.xml'
				],
				handleHttpError: 'fail'
			},
			inlineStyleThreshold: 20480
		})
	],
	resolve: {
		alias: {
			$lib: path.resolve('./src/lib'),
			$components: path.resolve('./src/lib/components'),
			$data: path.resolve('./src/lib/data')
		}
	},
	build: {
		target: 'es2020',
		assetsInlineLimit: 2048,
		rollupOptions: {
			output: {
				// GSAP is only needed once the page is interactive, so it is kept in
				// its own lazily-imported chunk and never blocks first paint.
				manualChunks(id) {
					if (id.includes('node_modules/gsap')) return 'gsap';
					return undefined;
				}
			}
		}
	}
});
