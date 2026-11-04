<script lang="ts">
	import Icon from './Icon.svelte';
	import { site, support } from '$data/site';

	/**
	 * The support ask, parked in the bottom-right corner of every page.
	 *
	 * Ko-fi's own overlay widget is a floating chat bubble loaded from their CDN;
	 * this is the same idea drawn in the site's hand: a cream pill with an ink
	 * border and a hard shadow, so it reads as one more sticker on the page
	 * instead of a stranger's branding. Nothing is loaded from Ko-fi until the
	 * reader clicks.
	 *
	 * It is fixed and always visible, on the landing page and on the app's own
	 * OAuth pages alike. No scroll threshold, no footer hand-off: the ask does not
	 * appear halfway through a read or vanish at the bottom of one.
	 */
</script>

<a class="support-fab" href={site.kofi} target="_blank" rel="noopener noreferrer">
	<Icon name="coffee" size={16} />
	<span class="support-fab__label">{support.fabLabel}</span>
</a>

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
		transition:
			background-color var(--dur-fast) var(--ease-out),
			box-shadow var(--dur-fast) var(--ease-out),
			transform var(--dur-fast) var(--ease-out);
	}

	.support-fab :global(svg) {
		flex: none;
		color: var(--accent);
	}

	/* The same lift and press as every other button on the page. */
	.support-fab:hover {
		transform: translate(-1px, -1px);
		box-shadow: 5px 5px 0 rgb(33 28 19 / 0.9);
		background: var(--paper);
	}

	.support-fab:active {
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

		.support-fab:hover,
		.support-fab:active {
			transform: none;
		}
	}
</style>
