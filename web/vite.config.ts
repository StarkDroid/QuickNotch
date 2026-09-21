import { sveltekit } from '@sveltejs/kit/vite';
import { defineConfig } from 'vite';

export default defineConfig({
	plugins: [sveltekit()],
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
