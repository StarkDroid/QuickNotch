<script lang="ts">
	import '../app.css';
	import '@fontsource-variable/fraunces/full.css';
	import '@fontsource-variable/inter';

	import { onMount } from 'svelte';
	import Grain from '$components/Grain.svelte';
	import Spotlight from '$components/Spotlight.svelte';
	import SupportFab from '$components/SupportFab.svelte';
	import { initSmoothScroll } from '$lib/motion/smoothScroll';

	// Hashed font URLs, so the display face can be preloaded for a faster LCP.
	// `full` includes the SOFT/WONK/opsz axes that give Fraunces its quirky
	// hand-set character — still a single ~120 KB file, swapped once.
	import displayFont from '@fontsource-variable/fraunces/files/fraunces-latin-full-normal.woff2?url';
	import bodyFont from '@fontsource-variable/inter/files/inter-latin-wght-normal.woff2?url';

	let { children } = $props();

	onMount(() => {
		let dispose: (() => void) | undefined;
		let cancelled = false;

		initSmoothScroll().then((cleanup) => {
			if (cancelled) cleanup();
			else dispose = cleanup;
		});

		return () => {
			cancelled = true;
			dispose?.();
		};
	});
</script>

<svelte:head>
	<link rel="preload" href={displayFont} as="font" type="font/woff2" crossorigin="anonymous" />
	<link rel="preload" href={bodyFont} as="font" type="font/woff2" crossorigin="anonymous" />
</svelte:head>

{@render children()}

<!--
	Site-wide backdrop, painted behind the page:
	  -2  the scroll-driven spotlight glow
	  -1  the film grain
	`body::before` supplies the flat base tint at -3.
-->
<Spotlight />
<Grain />
<SupportFab />
