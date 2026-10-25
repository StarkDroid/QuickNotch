<script lang="ts">
	import Icon from './Icon.svelte';
	import { buyCta, site } from '$data/site';

	/**
	 * The header is the notch: a dark capsule pinned to the top of the page with
	 * the mark, the wordmark and the buy button. At the top of the page it rests
	 * compact, hugging its content; scrolling opens it into a full-width bar and
	 * scrolling back up closes it again. There are no section links — the tour
	 * and the FAQ are found by scrolling, and the only destinations are the buy
	 * page and the top of this one.
	 *
	 * Only two pieces of state exist, both driven by one rAF-batched scroll
	 * listener: `scrolled` (which the CSS transition animates) and the progress
	 * line. Everything visual is a CSS transition, so both directions are smooth
	 * and nothing re-renders per frame.
	 */
	let scrolled = $state(false);
	let progress = $state(0);

	$effect(() => {
		let frame = 0;
		const update = () => {
			frame = 0;
			// A small threshold: the notch should be compact at the very top and
			// open as soon as the page starts moving, not a few pixels later.
			scrolled = window.scrollY > 10;
			const max = document.documentElement.scrollHeight - window.innerHeight;
			progress = max > 0 ? Math.min(1, window.scrollY / max) : 0;
		};
		const onScroll = () => {
			if (!frame) frame = requestAnimationFrame(update);
		};
		update();
		window.addEventListener('scroll', onScroll, { passive: true });
		window.addEventListener('resize', onScroll);
		return () => {
			if (frame) cancelAnimationFrame(frame);
			window.removeEventListener('scroll', onScroll);
			window.removeEventListener('resize', onScroll);
		};
	});
</script>

<a class="skip-link" href="#main">Skip to content</a>

<header class="nav" class:scrolled>
	<div class="nav__pill">
		<a class="brand" href="#top" aria-label="{site.name}, back to top">
			<span class="brand__mark"><Icon name="logo" size={18} /></span>
			<span class="brand__name">{site.name}</span>
		</a>

		<a
			class="buy"
			href={site.purchaseUrl}
			target="_blank"
			rel="noopener noreferrer"
		>
			<Icon name="cart" size={15} />
			<span>{buyCta}</span>
		</a>

		<span class="nav__progress" style:transform="scaleX({progress})" aria-hidden="true"></span>
	</div>
</header>

<style>
	.nav {
		position: sticky;
		top: 0;
		z-index: 100;
		/* Fixed height: the pill grows inside this box, so opening the notch never
		   shifts the page content below it (a changing sticky height would nudge
		   the whole layout as you cross the threshold). */
		height: var(--header-h);
		padding: 0.55rem var(--gutter) 0;
		pointer-events: none;
	}

	/* The notch itself. Width, radius and padding are the only things that
	   animate; the height steps once so the bar has a little more presence when
	   open. Both states are plain lengths, which is what lets them transition. */
	.nav__pill {
		position: relative;
		pointer-events: auto;
		display: flex;
		align-items: center;
		gap: 0.7rem;
		width: min(92%, 22.5rem);
		height: 3.05rem;
		margin-inline: auto;
		padding: 0 0.4rem 0 0.75rem;
		border-radius: 999px;
		border: 1px solid var(--on-dark-line-strong);
		background: rgb(10 10 10 / 0.94);
		backdrop-filter: blur(16px) saturate(140%);
		-webkit-backdrop-filter: blur(16px) saturate(140%);
		box-shadow:
			inset 0 1px 0 rgb(255 255 255 / 0.07),
			0 10px 26px -16px rgb(0 0 0 / 0.85);
		color: var(--on-dark);
		overflow: hidden;
		transition:
			width 560ms var(--ease-out),
			height 560ms var(--ease-out),
			padding 560ms var(--ease-out),
			border-radius 560ms var(--ease-out),
			background-color 360ms var(--ease-out),
			box-shadow 560ms var(--ease-out);
	}

	.nav.scrolled .nav__pill {
		width: min(100%, var(--maxw));
		height: 3.4rem;
		padding: 0 0.5rem 0 1rem;
		border-radius: 18px;
		box-shadow:
			inset 0 1px 0 rgb(255 255 255 / 0.07),
			0 16px 34px -22px rgb(0 0 0 / 0.9);
	}

	.brand {
		display: inline-flex;
		align-items: center;
		gap: 0.6rem;
		flex: none;
		color: var(--on-dark);
		font-family: var(--font-display);
		font-weight: 700;
		font-size: 1.02rem;
		letter-spacing: -0.015em;
		white-space: nowrap;
	}

	.brand__mark {
		display: grid;
		place-items: center;
		width: 30px;
		height: 30px;
		border-radius: 9px;
		background: var(--accent);
		color: #fff;
		box-shadow:
			inset 0 1px 0 rgb(255 255 255 / 0.28),
			inset 0 -1px 0 rgb(0 0 0 / 0.25);
	}

	.buy {
		display: inline-flex;
		align-items: center;
		gap: 0.45rem;
		margin-left: auto;
		padding: 0.58rem 0.95rem;
		border-radius: 999px;
		border: 1px solid rgb(0 0 0 / 0.5);
		background: var(--accent);
		color: var(--on-accent);
		font-family: var(--font-display);
		font-weight: 700;
		font-size: 0.9rem;
		line-height: 1;
		white-space: nowrap;
		box-shadow:
			inset 0 1px 0 rgb(255 255 255 / 0.22),
			0 2px 0 rgb(0 0 0 / 0.35);
		transition:
			background-color var(--dur-fast) var(--ease-out),
			transform var(--dur-fast) var(--ease-out);
	}

	.buy:hover {
		background: var(--tomato-deep);
		transform: translateY(-1px);
	}

	.buy:active {
		transform: translateY(1px);
	}

	/* Progress as a hairline inside the capsule, so it belongs to the notch
	   rather than to the page. */
	.nav__progress {
		position: absolute;
		left: 0.9rem;
		right: 0.9rem;
		bottom: 3px;
		height: 2px;
		border-radius: 2px;
		transform-origin: 0 50%;
		background: var(--accent);
		opacity: 0.8;
	}

	@media (max-width: 560px) {
		.nav__pill {
			width: min(96%, 22.5rem);
			padding-left: 0.6rem;
		}

		.nav.scrolled .nav__pill {
			width: 100%;
			padding-left: 0.7rem;
		}

		.brand {
			font-size: 0.95rem;
			gap: 0.5rem;
		}

		.brand__mark {
			width: 27px;
			height: 27px;
		}

		.buy {
			padding: 0.55rem 0.8rem;
			font-size: 0.84rem;
		}
	}

	@media (prefers-reduced-motion: reduce) {
		.nav__pill {
			transition: background-color 200ms, box-shadow 200ms;
		}
	}
</style>
