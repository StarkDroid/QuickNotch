<script lang="ts">
	import Icon from './Icon.svelte';
	import NotchPanel from './NotchPanel.svelte';
	import ScreenFrame from './ScreenFrame.svelte';
	import { downloadCta, hero, site } from '$data/site';
	import { loadMotion, prefersReducedMotion } from '$lib/motion/gsap';
	import { magnetic } from '$lib/actions/magnetic';
	import { scrollToId } from '$lib/motion/scroll';

	let copyEl: HTMLDivElement | undefined = $state();
	/** Reveals the hero immediately if the entrance timeline never runs. */
	let staticFallback = $state(false);

	$effect(() => {
		const copy = copyEl;
		if (!copy) return;

		let disposed = false;
		let started = false;
		let cleanup: (() => void) | undefined;

		const run = async () => {
			const lines = copy.querySelectorAll<HTMLElement>('[data-line]');

			if (prefersReducedMotion()) {
				staticFallback = true;
				return;
			}

			let gsap: typeof import('gsap').gsap;
			let ScrollTrigger: typeof import('gsap/ScrollTrigger').ScrollTrigger;
			try {
				({ gsap, ScrollTrigger } = await loadMotion());
			} catch {
				staticFallback = true;
				return;
			}
			if (disposed) return;
			started = true;

			// Scoped to the whole section, not just the copy block: the screen is a
			// sibling of the copy, so querying inside `copy` would skip it.
			const section = copy.closest('section') ?? copy;
			const extras = section.querySelectorAll<HTMLElement>('[data-hero-extra]');
			const screen = section.querySelector<HTMLElement>('.hero__screen');

			// The CSS guard hides these before first paint, so the tweens must state
			// both ends explicitly — a plain `.from()` would read the hidden state as
			// its destination and leave the hero invisible. Each headline line slides
			// up out of its own overflow-hidden mask.
			const timeline = gsap.timeline({ defaults: { ease: 'expo.out' } });
			timeline
				.fromTo(
					lines,
					{ yPercent: 115, opacity: 0 },
					{ yPercent: 0, opacity: 1, duration: 0.95, stagger: 0.08 }
				)
				.fromTo(
					extras,
					{ y: 24, opacity: 0 },
					{ y: 0, opacity: 1, duration: 0.8, stagger: 0.07 },
					'-=0.62'
				);

			// Gentle scroll parallax: copy drifts up, the screen settles back.
			const parallax = gsap.timeline({
				scrollTrigger: {
					trigger: section,
					start: 'top top',
					end: 'bottom top',
					scrub: 0.6
				}
			});
			parallax.to(copy, { yPercent: -7, opacity: 0.4, ease: 'none' }, 0);
			if (screen) parallax.to(screen, { yPercent: -3, scale: 0.985, ease: 'none' }, 0);

			cleanup = () => {
				timeline.kill();
				parallax.scrollTrigger?.kill();
				parallax.kill();
				ScrollTrigger.refresh();
			};
		};

		void run();

		// Belt and braces: if the import stalls, show the hero anyway.
		const guard = window.setTimeout(() => {
			if (!started) staticFallback = true;
		}, 2500);

		return () => {
			disposed = true;
			clearTimeout(guard);
			cleanup?.();
		};
	});
</script>

<section class="hero" class:hero--static={staticFallback} id="top">
	<!-- Poster decoration: a sunburst star, ink on paper, clipped to the hero. -->
	<div class="hero__deco" aria-hidden="true">
		<svg class="hero__burst" viewBox="0 0 120 120" fill="none">
			<path
				d="M60 8 L68 52 L112 60 L68 68 L60 112 L52 68 L8 60 L52 52 Z"
				stroke="currentColor"
				stroke-width="3"
				stroke-linejoin="round"
			/>
			<circle cx="60" cy="60" r="7" fill="currentColor" />
		</svg>
	</div>

	<div class="container hero__inner">
		<div class="hero__copy" bind:this={copyEl}>
			<span class="sticker sticker--paper" data-hero-extra>{hero.badge}</span>

			<h1 class="mega hero__title">
				{#each hero.headlineLines as line, index (index)}
					<span class="line-mask"><span data-line>{line}</span></span>
				{/each}
			</h1>

			<p class="lead hero__lead" data-hero-extra>{hero.sub}</p>

			<div class="hero__cta" data-hero-extra>
				<a class="btn btn--primary btn--lg" href={site.downloadPath} use:magnetic>
					<Icon name="download" size={18} />
					{downloadCta}
				</a>
				<button class="btn btn--ghost btn--lg" type="button" onclick={() => scrollToId('tour')}>
					{hero.secondaryCta}
					<Icon name="arrow-right" size={18} />
				</button>
			</div>

			<ul class="hero__trust" data-hero-extra>
				{#each hero.trust as item (item)}
					<li><Icon name="check" size={14} />{item}</li>
				{/each}
			</ul>

			<div class="hero__scrollhint mono" data-hero-extra aria-hidden="true">
				<span>{hero.scrollHint}</span>
				<svg viewBox="0 0 16 24" width="12" height="18" aria-hidden="true">
					<path d="M8 2 v14 M8 16 l-5 -5 M8 16 l5 -5" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round" />
				</svg>
			</div>
		</div>

		<div class="hero__screen" data-hero-extra>
			<ScreenFrame>
				{#snippet notch()}
					<NotchPanel state="apps" shape="amoled" />
				{/snippet}
			</ScreenFrame>

			<span class="sticker hero__screen-note" aria-hidden="true">{hero.screenNote}</span>
		</div>
	</div>
</section>

<style>
	.hero {
		position: relative;
		/* Deliberately tight: the headline should sit right under the nav rather
		   than leaving a gap of empty space at the top of the page. */
		padding-top: clamp(1rem, 2.5vw, 2.5rem);
		padding-bottom: clamp(2.5rem, 6vw, 5rem);
	}

	/* Poster set-dressing: a stamped sunburst, clipped so it never widens the
	   document. */
	.hero__deco {
		position: absolute;
		inset: 0;
		overflow: hidden;
		pointer-events: none;
		z-index: 0;
	}

	.hero__burst {
		position: absolute;
		top: clamp(1rem, 4vw, 3rem);
		right: calc(var(--gutter) * -0.15);
		width: clamp(6rem, 13vw, 10rem);
		height: clamp(6rem, 13vw, 10rem);
		color: rgb(33 28 19 / 0.08);
		rotate: 7deg;
	}

	.hero__inner {
		position: relative;
		z-index: 1;
		display: grid;
		gap: clamp(2.5rem, 5vw, 4.5rem);
		align-items: center;
	}

	.hero__copy {
		display: flex;
		flex-direction: column;
		align-items: flex-start;
		gap: 1.4rem;
		max-width: 40rem;
	}

	.hero__title {
		margin-block: 0.4rem 0.1rem;
		/* Two columns mean the poster type has to fit its own column; the full
		   `--fs-mega` was sized for a centred headline across the page. */
		font-size: clamp(2.4rem, 5.3vw, 4.75rem);
		letter-spacing: -0.035em;
	}

	.hero__title :global(.line-mask > span) {
		display: block;
	}

	/* One line carries the accent, as a flat colour rather than a gradient fill. */
	.hero__title :global(.line-mask:nth-child(2) > span) {
		color: var(--accent-text);
	}

	.hero__lead {
		max-width: 44ch;
	}

	.hero__cta {
		display: flex;
		flex-wrap: wrap;
		gap: 0.85rem;
	}

	.hero__trust {
		display: flex;
		flex-wrap: wrap;
		gap: 0.6rem 1rem;
		font-size: var(--fs-small);
		color: var(--text-3);
	}

	.hero__trust li {
		display: inline-flex;
		align-items: center;
		gap: 0.4rem;
	}

	.hero__trust :global(svg) {
		color: var(--green);
	}

	.hero__scrollhint {
		display: inline-flex;
		align-items: center;
		gap: 0.5rem;
		margin-top: 0.25rem;
		color: var(--text-3);
	}

	.hero__scrollhint svg {
		animation: hero-nudge 1.8s var(--ease-inout) infinite;
	}

	@keyframes hero-nudge {
		0%,
		100% {
			transform: translateY(0);
		}
		50% {
			transform: translateY(4px);
		}
	}

	.hero__screen {
		position: relative;
		width: 100%;
		max-width: 40rem;
		margin-inline: auto;
	}

	/* A torn-decal note slapped onto the mock, like a caption on a print proof. */
	.hero__screen-note {
		position: absolute;
		bottom: 1.1rem;
		left: -0.6rem;
		z-index: 2;
		rotate: -3deg;
	}

	/* --- Entrance guard ----------------------------------------------------
	   The hero hides itself from first paint so nothing flashes while GSAP is
	   being fetched, then the timeline animates from exactly these values (see
	   the `fromTo` tweens). With JavaScript disabled the `.js` flag is absent,
	   and `html.reduced-motion` / `.hero--static` cover the reduced-motion and
	   import-failure cases, so the headline can never stay invisible.

	   The guard deliberately only touches `opacity`. A percentage `translate`
	   here would be parsed by GSAP as a *pixel* `y` offset, which then survives
	   the `yPercent` tween and leaves every line pushed down out of its mask. */
	:global(.js .hero__title .line-mask > span) {
		opacity: 0;
	}

	:global(.js .hero [data-hero-extra]) {
		opacity: 0;
	}

	:global(html:not(.js) .hero__title .line-mask > span),
	:global(html.reduced-motion .hero__title .line-mask > span),
	:global(.js .hero.hero--static .hero__title .line-mask > span),
	:global(html:not(.js) .hero [data-hero-extra]),
	:global(html.reduced-motion .hero [data-hero-extra]),
	:global(.js .hero.hero--static [data-hero-extra]) {
		opacity: 1;
		transform: none;
	}

	@media (min-width: 960px) {
		.hero__inner {
			grid-template-columns: minmax(0, 1.04fr) minmax(0, 0.96fr);
			gap: clamp(2rem, 4vw, 4rem);
		}

		.hero__screen {
			max-width: none;
		}

		.hero__lead {
			max-width: 40ch;
		}
	}

	@media (max-width: 959px) {
		.hero__copy {
			align-items: center;
			text-align: center;
			margin-inline: auto;
		}

		.hero__lead {
			margin-inline: auto;
		}

		.hero__cta,
		.hero__trust {
			justify-content: center;
		}

		.hero__screen-note {
			left: 0.5rem;
		}
	}
</style>
