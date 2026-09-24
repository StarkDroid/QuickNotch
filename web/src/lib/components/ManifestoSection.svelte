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
		{ n: '01', text: 'Stop hunting in menus.' },
		{ n: '02', text: 'Make every pixel yours.' },
		{ n: '03', text: 'Click once. There it is.' }
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
		display: grid;
		grid-template-columns: auto 1fr;
		align-items: baseline;
		column-gap: clamp(1rem, 3vw, 2.5rem);
		row-gap: 1.25rem;
		min-height: clamp(14rem, 38vh, 26rem);
		padding-block: 1rem;
	}

	.manifesto__index {
		flex: none;
		display: grid;
		place-items: center;
		width: 2.6rem;
		height: 2.6rem;
		border-radius: 50%;
		border: 2px solid var(--ink);
		background: var(--paper-lift);
		color: var(--accent-text);
		box-shadow: 3px 3px 0 rgb(33 28 19 / 0.85);
	}

	.manifesto__text {
		font-family: var(--font-display);
		font-weight: 700;
		font-size: clamp(2rem, 6vw, 4.75rem);
		line-height: 0.98;
		letter-spacing: -0.035em;
		color: var(--text);
		text-wrap: balance;
		max-width: 16ch;
		font-variation-settings:
			'SOFT' 52,
			'WONK' 0.72;
	}

	/* The closing statement answers in the loud accent — the "stamp" of the
	   poster. */
	.manifesto__line:last-child .manifesto__text {
		color: var(--accent-text);
	}

	/* Ticket stub under each line, like a tear-off. */
	.manifesto__line::after {
		content: '';
		grid-column: 2;
		margin-top: 1.5rem;
		border-top: 2px dashed var(--line-strong);
	}

	.manifesto__line:nth-child(2) {
		margin-left: clamp(0rem, 8vw, 8rem);
	}

	.manifesto__line:nth-child(3) {
		margin-left: clamp(0rem, 16vw, 16rem);
	}
</style>
