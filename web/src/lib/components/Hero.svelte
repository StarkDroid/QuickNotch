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

	// Subtle pointer tilt on the mock screen: the screen leans a couple of
	// degrees toward the cursor on fine-pointer devices. Transform-only and
	// batched through one rAF, so it never triggers layout. Disabled entirely
	// for touch and reduced-motion visitors.
	let screenWrap: HTMLDivElement | undefined = $state();
	let tiltRunning = false;
	let tiltTarget = { rx: 0, ry: 0, tx: 0, ty: 0 };
	let tiltCurrent = { rx: 0, ry: 0, tx: 0, ty: 0 };
	let tiltRaf = 0;

	function tiltFrame() {
		tiltRaf = requestAnimationFrame(tiltFrame);
		const k = 0.12;
		tiltCurrent.rx += (tiltTarget.rx - tiltCurrent.rx) * k;
		tiltCurrent.ry += (tiltTarget.ry - tiltCurrent.ry) * k;
		tiltCurrent.tx += (tiltTarget.tx - tiltCurrent.tx) * k;
		tiltCurrent.ty += (tiltTarget.ty - tiltCurrent.ty) * k;
		if (screenWrap) {
			screenWrap.style.transform = `perspective(1100px) rotateX(${tiltCurrent.rx.toFixed(2)}deg) rotateY(${tiltCurrent.ry.toFixed(2)}deg) translate3d(${tiltCurrent.tx.toFixed(1)}px, ${tiltCurrent.ty.toFixed(1)}px, 0)`;
		}
		const settled =
			Math.abs(tiltTarget.rx - tiltCurrent.rx) < 0.01 &&
			Math.abs(tiltTarget.ry - tiltCurrent.ry) < 0.01 &&
			Math.abs(tiltTarget.tx - tiltCurrent.tx) < 0.05 &&
			Math.abs(tiltTarget.ty - tiltCurrent.ty) < 0.05;
		if (settled) {
			cancelAnimationFrame(tiltRaf);
			tiltRaf = 0;
			tiltRunning = false;
		}
	}

	function onScreenMove(event: PointerEvent) {
		if (window.matchMedia('(hover: none), (pointer: coarse)').matches) return;
		if (prefersReducedMotion()) return;
		if (!screenWrap) return;
		const rect = screenWrap.getBoundingClientRect();
		const px = (event.clientX - rect.left) / rect.width;
		const py = (event.clientY - rect.top) / rect.height;
		tiltTarget.ry = (px - 0.5) * 5;
		tiltTarget.rx = -(py - 0.5) * 4;
		tiltTarget.tx = (px - 0.5) * 10;
		tiltTarget.ty = (py - 0.5) * 8;
		if (!tiltRunning && !tiltRaf) {
			tiltRunning = true;
			tiltRaf = requestAnimationFrame(tiltFrame);
		}
	}

	function onScreenLeave() {
		if (window.matchMedia('(hover: none), (pointer: coarse)').matches) return;
		tiltTarget.rx = 0;
		tiltTarget.ry = 0;
		tiltTarget.tx = 0;
		tiltTarget.ty = 0;
		if (!tiltRunning && !tiltRaf) {
			tiltRunning = true;
			tiltRaf = requestAnimationFrame(tiltFrame);
		}
	}

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

	// Tear down the tilt loop on unmount so a stale rAF never touches a dead node.
	$effect(() => {
		return () => {
			if (tiltRaf) cancelAnimationFrame(tiltRaf);
			tiltRaf = 0;
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
		<span class="hero__diag mono" aria-hidden="true">hand-numbered · edition no. 1</span>
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
				<span>the good stuff is below</span>
				<svg viewBox="0 0 16 24" width="12" height="18" aria-hidden="true">
					<path d="M8 2 v14 M8 16 l-5 -5 M8 16 l5 -5" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round" />
				</svg>
			</div>
		</div>

		<div class="hero__screen" data-hero-extra>
			<div
				class="hero__tilt"
				role="presentation"
				bind:this={screenWrap}
				onpointermove={onScreenMove}
				onpointerleave={onScreenLeave}
			>
				<ScreenFrame tall>
					{#snippet notch()}
						<NotchWidgetDemo theme="amoled" label="Notch widgets — scroll to change" />
					{/snippet}
				</ScreenFrame>
			</div>

			<span class="sticker hero__screen-note" aria-hidden="true">docked in an instant ★</span>

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

	/* Poster set-dressing: a stamped sunburst and a hand-written edge note.
	   Absolutely positioned so they never affect layout, clipped so they never
	   push the document wider than the viewport. */
	.hero__deco {
		position: absolute;
		inset: 0;
		overflow: hidden;
		pointer-events: none;
		z-index: 0;
	}

	.hero__burst {
		position: absolute;
		top: clamp(2rem, 7vw, 5rem);
		right: calc(var(--gutter) * -0.15);
		width: clamp(7rem, 17vw, 12rem);
		height: clamp(7rem, 17vw, 12rem);
		color: rgb(33 28 19 / 0.08);
		rotate: 7deg;
	}

	.hero__diag {
		position: absolute;
		right: clamp(1rem, 6vw, 4rem);
		bottom: clamp(1rem, 4vw, 2.5rem);
		color: var(--ink-3);
		opacity: 0.7;
		rotate: -3deg;
		pointer-events: none;
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
		margin-block: 0.6rem 0.2rem;
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
		gap: 0.85rem;
	}

	.hero__trust {
		display: flex;
		flex-wrap: wrap;
		justify-content: center;
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
		max-width: 68rem;
		width: 100%;
		margin-inline: auto;
	}

	/* The tilt wrapper carries the pointer-drive perspective spin; at rest it must
	   add nothing to layout, so the entrance parallax / layout stays untouched. */
	.hero__tilt {
		transform-style: preserve-3d;
		will-change: transform;
	}

	/* A torn-decal note slapped onto the mock, like a caption on a print proof. */
	.hero__screen-note {
		position: absolute;
		top: 1.5rem;
		left: clamp(0.5rem, 4vw, 3rem);
		z-index: 2;
		rotate: -4deg;
	}

	.hero__platforms {
		display: flex;
		flex-wrap: wrap;
		justify-content: center;
		gap: 0.8rem;
		margin-top: 1.75rem;
	}

	.platform {
		display: inline-flex;
		align-items: center;
		gap: 0.7rem;
		padding: 0.6rem 1rem;
		border-radius: var(--r-sm);
		border: 2px solid var(--ink);
		background: var(--paper-lift);
		box-shadow: 3px 3px 0 rgb(33 28 19 / 0.85);
		transition: transform var(--dur-fast) var(--ease-out), box-shadow var(--dur) var(--ease-out),
			background-color var(--dur) var(--ease-out);
	}

	.platform:hover {
		transform: translate(-1px, -1px);
		box-shadow: 4px 4px 0 rgb(33 28 19 / 0.85);
		background: var(--paper-deep);
	}

	.platform:nth-child(2) {
		rotate: 0.6deg;
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
