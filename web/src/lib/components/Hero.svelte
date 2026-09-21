<script lang="ts">
	import Icon from './Icon.svelte';
	import NotchWidgetDemo from './NotchWidgetDemo.svelte';
	import ScreenFrame from './ScreenFrame.svelte';
	import { hero, platforms, site } from '$data/site';
	import { loadMotion, prefersReducedMotion } from '$lib/motion/gsap';
	import { magnetic } from '$lib/actions/magnetic';
	import { scrollToId } from '$lib/motion/smoothScroll';

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

			// Scoped to the whole section, not just the copy block: `.hero__screen` is
			// a sibling of `.hero__copy`, so querying inside `copy` would skip it.
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

			// Gentle scroll parallax: copy drifts up, the mock screen settles back.
			const parallax = gsap.timeline({
				scrollTrigger: {
					trigger: section,
					start: 'top top',
					end: 'bottom top',
					scrub: 0.6
				}
			});
			parallax.to(copy, { yPercent: -9, opacity: 0.35, ease: 'none' }, 0);
			if (screen) parallax.to(screen, { yPercent: -4, scale: 0.985, ease: 'none' }, 0);

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
	<div class="container hero__inner">
		<div class="hero__copy" bind:this={copyEl}>
			<h1 class="mega hero__title">
				{#each hero.headlineLines as line, index (index)}
					<span class="line-mask"><span data-line>{line}</span></span>
				{/each}
			</h1>

			<p class="lead hero__lead" data-hero-extra>{hero.sub}</p>

			<div class="hero__cta" data-hero-extra>
				<a
					class="btn btn--primary btn--lg"
					href={site.downloadUrl}
					use:magnetic
					target="_blank"
					rel="noopener noreferrer"
				>
					<Icon name="download" size={18} />
					{hero.primaryCta}
				</a>
				<button class="btn btn--ghost btn--lg" type="button" onclick={() => scrollToId('widgets')}>
					See how it works
					<Icon name="arrow-right" size={18} />
				</button>
			</div>

			<ul class="hero__trust" data-hero-extra>
				{#each hero.trust as item (item)}
					<li><Icon name="check" size={14} />{item}</li>
				{/each}
			</ul>
		</div>

		<div class="hero__screen" data-hero-extra>
			<ScreenFrame tall>
				{#snippet notch()}
					<NotchWidgetDemo theme="amoled" label="Notch widgets — scroll to change" />
				{/snippet}
			</ScreenFrame>

			<div class="hero__platforms">
				{#each platforms as platform (platform.id)}
					<a
						class="platform"
						href={site.downloadUrl}
						target="_blank"
						rel="noopener noreferrer"
					>
						<span class="platform__icon"><Icon name={platform.icon} size={20} /></span>
						<span class="platform__text">
							<strong>{platform.name}</strong>
							<span>{platform.note}</span>
						</span>
						<Icon name="arrow-up-right" size={16} />
					</a>
				{/each}
			</div>
		</div>
	</div>
</section>

<style>
	.hero {
		position: relative;
		/* Deliberately tight: the headline should sit right under the nav rather
		   than leaving a gap of empty space at the top of the page. */
		padding-top: clamp(0.75rem, 2vw, 2rem);
		padding-bottom: clamp(4rem, 9vw, 8rem);
	}

	.hero__inner {
		position: relative;
		z-index: 1;
		display: grid;
		gap: clamp(2.5rem, 5vw, 4.5rem);
	}

	.hero__copy {
		display: flex;
		flex-direction: column;
		align-items: center;
		text-align: center;
		gap: 1.5rem;
		max-width: 62rem;
		margin-inline: auto;
	}

	.hero__title {
		margin-block: 0.2rem;
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
		max-width: 54ch;
		margin-inline: auto;
	}

	.hero__cta {
		display: flex;
		flex-wrap: wrap;
		justify-content: center;
		gap: 0.75rem;
	}

	.hero__trust {
		display: flex;
		flex-wrap: wrap;
		justify-content: center;
		gap: 0.6rem 1.5rem;
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

	.hero__screen {
		position: relative;
		max-width: 68rem;
		width: 100%;
		margin-inline: auto;
	}

	.hero__platforms {
		display: flex;
		flex-wrap: wrap;
		justify-content: center;
		gap: 0.75rem;
		margin-top: 1.5rem;
	}

	.platform {
		display: inline-flex;
		align-items: center;
		gap: 0.7rem;
		padding: 0.6rem 1rem;
		border-radius: var(--r-pill);
		border: 1px solid var(--line);
		background: rgb(15 23 42 / 0.03);
		backdrop-filter: blur(10px);
		transition: border-color var(--dur) var(--ease-out), background-color var(--dur) var(--ease-out);
	}

	.platform:hover {
		border-color: var(--line-strong);
		background: rgb(15 23 42 / 0.05);
	}

	.platform__icon {
		display: grid;
		place-items: center;
		color: var(--text);
	}

	.platform__text {
		display: flex;
		flex-direction: column;
		line-height: 1.25;
	}

	.platform__text strong {
		font-family: var(--font-display);
		font-size: 0.9rem;
		color: var(--text);
	}

	.platform__text span {
		font-size: var(--fs-micro);
		color: var(--text-3);
	}

	.platform :global(svg:last-child) {
		color: var(--text-3);
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

	@media (min-width: 720px) {
		.hero__lead {
			max-width: 48ch;
		}
	}
</style>
