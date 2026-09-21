<script lang="ts">
	import { onMount } from 'svelte';
	import { prefersReducedMotion } from '$lib/motion/gsap';

	/**
	 * Scroll-driven spotlight.
	 *
	 * A fixed layer holding two heavily blurred flat discs. It rests near the top
	 * of the viewport as the hero's "glow from above", then follows the reading
	 * position down the page, settling on one focal element per section and
	 * finishing on the download buttons.
	 *
	 * Two deliberate choices:
	 *  - The glow is flat colour blurred with `filter: blur()`, not a
	 *    `radial-gradient`, so the design system stays gradient-free while still
	 *    producing soft light.
	 *  - Target rects are read once per frame in a read-then-write batch rather
	 *    than cached in document coordinates, because one of the targets is a
	 *    `position: sticky` panel whose document position changes while scrolling.
	 *    The loop stops as soon as the glow has settled and scrolling has paused.
	 */

	interface Props {
		/** Focal elements to spotlight, in reading order. */
		targets?: string[];
	}

	let {
		targets = [
			'#widgets .stage',
			'#themes .grid',
			'#launch .cards',
			'#download .actions'
		]
	}: Props = $props();

	let coreEl: HTMLSpanElement | undefined = $state();
	let haloEl: HTMLSpanElement | undefined = $state();

	/** Where the glow likes to sit vertically, as a fraction of the viewport. */
	const FOCAL = 0.42;
	/** Resting spot when no section target is in view: high up, as the top glow. */
	const REST_Y = 0.14;
	const REST_SCALE = 1.5;

	let els: HTMLElement[] = [];
	let targetX = 0;
	let targetY = 0;
	let targetScale = REST_SCALE;
	let curX = 0;
	let curY = 0;
	let curScale = REST_SCALE;
	let rafId = 0;
	let last = 0;
	let lastScrollAt = 0;
	let ready = false;

	function updateTarget() {
		const vw = window.innerWidth;
		const vh = window.innerHeight;
		const focalY = vh * FOCAL;

		let best: DOMRect | null = null;
		let bestDist = Infinity;

		for (const el of els) {
			const rect = el.getBoundingClientRect();
			if (rect.width === 0 && rect.height === 0) continue;
			if (rect.bottom < -40 || rect.top > vh + 40) continue;

			const centre = rect.top + rect.height / 2;
			const distance = Math.abs(centre - focalY);
			if (distance < bestDist) {
				bestDist = distance;
				best = rect;
			}
		}

		if (best) {
			targetX = best.left + best.width / 2;
			targetY = best.top + best.height / 2;
			targetScale = Math.min(2.2, Math.max(0.85, Math.max(best.width, best.height) / 420));
		} else {
			targetX = vw / 2;
			targetY = vh * REST_Y;
			targetScale = REST_SCALE;
		}
	}

	function apply() {
		if (coreEl) {
			coreEl.style.transform = `translate3d(${curX.toFixed(2)}px, ${curY.toFixed(2)}px, 0) scale(${curScale.toFixed(4)})`;
		}
		if (haloEl) {
			haloEl.style.transform = `translate3d(${curX.toFixed(2)}px, ${curY.toFixed(2)}px, 0) scale(${(curScale * 1.06).toFixed(4)})`;
		}
	}

	function frame(now: number) {
		rafId = requestAnimationFrame(frame);
		const dt = Math.min((now - last) / 1000, 0.05);
		last = now;

		// Read phase, then write phase — one layout flush per frame.
		updateTarget();
		const alpha = 1 - Math.exp(-7 * dt);
		curX += (targetX - curX) * alpha;
		curY += (targetY - curY) * alpha;
		curScale += (targetScale - curScale) * alpha;
		apply();

		const settled =
			Math.abs(targetX - curX) < 0.4 &&
			Math.abs(targetY - curY) < 0.4 &&
			Math.abs(targetScale - curScale) < 0.002;

		if (settled && now - lastScrollAt > 320) {
			stop();
		}
	}

	function start() {
		if (rafId || !ready) return;
		last = performance.now();
		rafId = requestAnimationFrame(frame);
	}

	function stop() {
		if (!rafId) return;
		cancelAnimationFrame(rafId);
		rafId = 0;
	}

	onMount(() => {
		els = targets
			.map((selector) => document.querySelector<HTMLElement>(selector))
			.filter((el): el is HTMLElement => Boolean(el));

		const vw = window.innerWidth;
		const vh = window.innerHeight;
		curX = targetX = vw / 2;
		curY = targetY = vh * REST_Y;
		ready = true;
		apply();

		const reduced = prefersReducedMotion();
		if (reduced) {
			// Static glow: positioned once, never tracked.
			return;
		}

		const onScroll = () => {
			lastScrollAt = performance.now();
			start();
		};
		const onResize = () => {
			lastScrollAt = performance.now();
			start();
		};
		const onVisibility = () => {
			if (document.hidden) stop();
			else start();
		};

		window.addEventListener('scroll', onScroll, { passive: true });
		window.addEventListener('resize', onResize);
		document.addEventListener('visibilitychange', onVisibility);

		// Fonts and reveal animations change section heights; re-settle after they
		// land so the spotlight is not left pointing at a stale position.
		document.fonts?.ready.then(() => start()).catch(() => {});

		start();

		return () => {
			stop();
			window.removeEventListener('scroll', onScroll);
			window.removeEventListener('resize', onResize);
			document.removeEventListener('visibilitychange', onVisibility);
		};
	});
</script>

<div class="spotlight" aria-hidden="true">
	<span class="spotlight__disc spotlight__halo" bind:this={haloEl}></span>
	<span class="spotlight__disc spotlight__core" bind:this={coreEl}></span>
</div>

<style>
	.spotlight {
		position: fixed;
		inset: 0;
		z-index: -2;
		overflow: hidden;
		pointer-events: none;
		--core-size: 360px;
		--halo-size: 820px;
	}

	.spotlight__disc {
		position: absolute;
		top: 0;
		left: 0;
		border-radius: 50%;
		will-change: transform;
	}

	.spotlight__core {
		width: var(--core-size);
		height: var(--core-size);
		margin-left: calc(var(--core-size) / -2);
		margin-top: calc(var(--core-size) / -2);
		background: var(--indigo-light);
		opacity: 0.28;
		filter: blur(calc(var(--core-size) * 0.22));
	}

	.spotlight__halo {
		width: var(--halo-size);
		height: var(--halo-size);
		margin-left: calc(var(--halo-size) / -2);
		margin-top: calc(var(--halo-size) / -2);
		background: var(--cyan);
		opacity: 0.12;
		filter: blur(calc(var(--halo-size) * 0.16));
	}

	@media (max-width: 700px) {
		.spotlight {
			--core-size: 240px;
			--halo-size: 500px;
		}
	}
</style>
