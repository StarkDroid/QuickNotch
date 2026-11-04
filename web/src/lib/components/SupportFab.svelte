<script lang="ts">
	import { onMount } from 'svelte';
	import { page } from '$app/state';
	import Icon from './Icon.svelte';
	import { site } from '$data/site';

	/**
	 * The support ask, parked in the bottom-right corner.
	 *
	 * Ko-fi's own overlay widget is a floating chat bubble loaded from their CDN;
	 * this is the same idea drawn in the site's hand — a cream pill with an ink
	 * border and a hard shadow, so it reads as one more sticker on the page
	 * instead of a stranger's branding. Nothing is loaded from Ko-fi until the
	 * reader clicks.
	 *
	 * It stays away until the reader has scrolled past the hero (the hero has
	 * one call to action, and this is not it) and leaves again when the footer
	 * is in view, because the footer already carries the same button. `/connect`
	 * and the OAuth callback belong to the app, not to the reader, so they get
	 * no ask at all.
	 */
	let scrolled = $state(false);
	let footerInView = $state(false);

	const hidden = $derived(
		page.url.pathname.startsWith('/connect') || page.url.pathname.startsWith('/oauth')
	);
	const shown = $derived(scrolled && !footerInView);

	onMount(() => {
		let frame = 0;

		const measure = () => {
			frame = 0;
			scrolled = window.scrollY > 480;
		};

		const onScroll = () => {
			if (frame) return;
			frame = requestAnimationFrame(measure);
		};

		const footer = document.querySelector('footer');
		const observer = footer
			? new IntersectionObserver(
					([entry]) => {
						footerInView = entry.isIntersecting;
					},
					{ threshold: 0.05 }
				)
			: null;

		if (footer) observer?.observe(footer);

		measure();
		window.addEventListener('scroll', onScroll, { passive: true });
		window.addEventListener('resize', onScroll, { passive: true });

		return () => {
			if (frame) cancelAnimationFrame(frame);
			observer?.disconnect();
			window.removeEventListener('scroll', onScroll);
			window.removeEventListener('resize', onScroll);
		};
	});
</script>

{#if !hidden}
	<a
		class="support-fab"
		class:support-fab--in={shown}
		href={site.kofi}
		target="_blank"
		rel="noopener noreferrer"
		aria-hidden={!shown}
		tabindex={shown ? 0 : -1}
	>
		<Icon name="coffee" size={16} />
		<span class="support-fab__label">Support me</span>
	</a>
{/if}

<style>
	.support-fab {
		position: fixed;
		right: clamp(1rem, 3vw, 1.75rem);
		bottom: clamp(1rem, 3vw, 1.75rem);
		z-index: 90;
		display: inline-flex;
		align-items: center;
		gap: 0.55rem;
		padding: 0.72rem 1.1rem 0.72rem 0.9rem;
		border: 2px solid var(--ink);
		border-radius: var(--r-pill);
		background: var(--paper-lift);
		color: var(--ink);
		font-family: var(--font-display);
		font-size: 0.95rem;
		font-weight: 700;
		line-height: 1;
		letter-spacing: -0.01em;
		white-space: nowrap;
		box-shadow: 4px 4px 0 rgb(33 28 19 / 0.9);
		opacity: 0;
		visibility: hidden;
		transform: translateY(10px);
		transition:
			opacity var(--dur) var(--ease-out),
			transform var(--dur) var(--ease-out),
			visibility var(--dur) step-end,
			background-color var(--dur-fast) var(--ease-out),
			box-shadow var(--dur-fast) var(--ease-out);
	}

	.support-fab--in {
		opacity: 1;
		visibility: visible;
		transform: none;
		transition-delay: 0s;
		transition-timing-function: var(--ease-out);
	}

	.support-fab :global(svg) {
		flex: none;
		color: var(--accent);
	}

	/* The same lift and press as every other button on the page. */
	.support-fab--in:hover {
		transform: translate(-1px, -1px);
		box-shadow: 5px 5px 0 rgb(33 28 19 / 0.9);
		background: var(--paper);
	}

	.support-fab--in:active {
		transform: translate(2px, 2px);
		box-shadow: 1px 1px 0 rgb(33 28 19 / 0.9);
	}

	/* A phone gets the cup alone; the label stays in the accessibility tree so
	   the link is never an unlabelled glyph. */
	@media (max-width: 560px) {
		.support-fab {
			padding: 0.78rem;
		}

		.support-fab__label {
			position: absolute;
			width: 1px;
			height: 1px;
			padding: 0;
			margin: -1px;
			overflow: hidden;
			clip-path: inset(50%);
			white-space: nowrap;
			border: 0;
		}
	}

	@media (prefers-reduced-motion: reduce) {
		.support-fab {
			transition: none;
		}
	}
</style>
