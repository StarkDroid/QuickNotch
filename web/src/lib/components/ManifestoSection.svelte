<script lang="ts">
	import { loadMotion, prefersReducedMotion } from '$lib/motion/gsap';

	/**
	 * Scroll-driven kinetic type.
	 *
	 * Three big statements brighten and rise into place as they cross the reading
	 * line, scrubbed to the scroll position rather than triggered once — the
	 * "big fonts animated by scrolling" moment the page was briefed with.
	 *
	 * The statements are plain text with no JS-hidden state, so without
	 * JavaScript (or under reduced motion) they render fully readable and the
	 * animation is simply absent.
	 */

	const statements = [
		{ n: '01', text: 'Everything, one glance.' },
		{ n: '02', text: 'Style it your way.' },
		{ n: '03', text: 'Launch from anywhere.' }
	];

	let rootEl: HTMLElement | undefined = $state();

	$effect(() => {
		const root = rootEl;
		if (!root) return;
		if (prefersReducedMotion()) return;

		let disposed = false;
		let cleanup: (() => void) | undefined;

		void (async () => {
			const { gsap, ScrollTrigger } = await loadMotion();
			if (disposed) return;

			const lines = root.querySelectorAll<HTMLElement>('[data-m-line]');
			const tweens: ReturnType<typeof gsap.fromTo>[] = [];

			lines.forEach((line) => {
				tweens.push(
					gsap.fromTo(
						line,
						{ opacity: 0.14, y: 64 },
						{
							opacity: 1,
							y: 0,
							ease: 'none',
							scrollTrigger: {
								trigger: line,
								start: 'top 86%',
								end: 'top 40%',
								scrub: 0.5
							}
						}
					)
				);
			});

			cleanup = () => {
				tweens.forEach((tween) => {
					tween.scrollTrigger?.kill();
					tween.kill();
				});
				ScrollTrigger.refresh();
			};
		})();

		return () => {
			disposed = true;
			cleanup?.();
		};
	});
</script>

<section class="manifesto" bind:this={rootEl} aria-label="How QuickNotch works">
	{#each statements as statement (statement.n)}
		<div class="manifesto__line">
			<span class="manifesto__index mono">{statement.n}</span>
			<p class="manifesto__text" data-m-line>{statement.text}</p>
		</div>
	{/each}
</section>

<style>
	.manifesto {
		position: relative;
		display: flex;
		flex-direction: column;
		gap: 1rem;
		padding-block: clamp(4rem, 10vw, 10rem);
	}

	.manifesto__line {
		display: flex;
		align-items: baseline;
		gap: clamp(1rem, 3vw, 2.5rem);
		min-height: clamp(14rem, 38vh, 26rem);
		padding-block: 1rem;
	}

	.manifesto__index {
		flex: none;
		color: var(--accent-text);
	}

	.manifesto__text {
		font-family: var(--font-display);
		font-weight: 700;
		font-size: clamp(2rem, 6vw, 4.75rem);
		line-height: 0.98;
		letter-spacing: -0.035em;
		color: var(--text);
		text-wrap: balance;
		max-width: 14ch;
	}

	.manifesto__line:nth-child(2) {
		margin-left: clamp(0rem, 8vw, 8rem);
	}

	.manifesto__line:nth-child(3) {
		margin-left: clamp(0rem, 16vw, 16rem);
	}
</style>
