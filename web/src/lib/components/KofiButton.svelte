<script lang="ts">
	import { site, support } from '$data/site';

	/**
	 * Ko-fi's published button artwork, used unchanged.
	 *
	 * The file is Ko-fi's own 580×146 PNG (cream fill, ink lettering, tomato
	 * heart) committed to `static/` rather than hot-linked: no runtime call to
	 * their CDN, no layout shift if it is slow, and the button keeps working if
	 * they ever move it. Their cream is close enough to this site's paper that it
	 * needs no frame.
	 */
	interface Props {
		/** Smaller presentation for tighter rows (the ticket serial line). */
		compact?: boolean;
	}

	let { compact = false }: Props = $props();
</script>

<a
	class="kofi"
	class:kofi--compact={compact}
	href={site.kofi}
	target="_blank"
	rel="noopener noreferrer"
>
	<img
		src="/kofi-button.png"
		alt={support.kofiAlt}
		width="580"
		height="146"
		decoding="async"
	/>
</a>

<style>
	.kofi {
		display: inline-flex;
		border-radius: 10px;
		transition:
			transform var(--dur-fast) var(--ease-out),
			filter var(--dur-fast) var(--ease-out);
	}

	.kofi img {
		display: block;
		height: 2.6rem;
		width: auto;
	}

	.kofi--compact img {
		height: 2rem;
	}

	.kofi:hover {
		transform: translateY(-1px);
		filter: saturate(108%) brightness(1.03);
	}

	.kofi:active {
		transform: translateY(1px);
	}

	@media (prefers-reduced-motion: reduce) {
		.kofi:hover,
		.kofi:active {
			transform: none;
		}
	}
</style>
