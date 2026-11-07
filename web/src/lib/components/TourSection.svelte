<script lang="ts">
	import TourDevice from './TourDevice.svelte';
	import { tour } from '$data/site';
	import { prefersReducedMotion } from '$lib/motion/gsap';
	import { reveal } from '$lib/actions/reveal';

	/**
	 * The tour: every widget explained in one place.
	 *
	 * A tall section with a sticky stage. The device swings from a skewed
	 * three-quarter view to frontal as the lid opens, then the screen walks
	 * through the seven states while the copy beside it swaps.
	 * The section is deliberately the only place widgets are described — it
	 * replaced four separate sections (widget cards, hover demo, gaming page,
	 * theme cards) and the copy is one sentence per state.
	 *
	 * Scroll is read on rAF, not per event: progress is written straight to CSS
	 * variables on the device so a frame never touches Svelte state. Only the
	 * step index crosses into the component, and only when it changes.
	 *
	 * Everything degrades: no JS or reduced motion un-sticks the stage and shows
	 * all seven captions as a plain list, with the device open on the quickview.
	 */

	let pinEl: HTMLElement | undefined = $state();
	let deviceEl: HTMLElement | undefined = $state();
	let step = $state(0);

	const steps = tour.steps;
	const total = String(steps.length).padStart(2, '0');

	/** Phase boundaries as a fraction of the pin's scrollable span. The lid
	 * starts opening almost immediately while the yaw is still settling out,
	 * so the deck has a moment as a visible surface mid-turn — by the time the
	 * device is frontal the lid is up and the deck is edge-on. */
	const SETTLE_END = 0.22;
	const OPEN_START = 0.0;
	const OPEN_END = 0.3;
	const STEPS_START = 0.3;

	const clamp01 = (value: number): number => Math.min(1, Math.max(0, value));
	const easeOut = (value: number): number => 1 - Math.pow(1 - value, 3);

	$effect(() => {
		const pin = pinEl;
		const device = deviceEl;
		if (!pin || !device) return;
		if (prefersReducedMotion()) return;

		let frame = 0;
		let disposed = false;

		const onScroll = () => {
			if (!frame) frame = requestAnimationFrame(paint);
		};

		const paint = () => {
			frame = 0;
			const rect = pin.getBoundingClientRect();
			const travel = rect.height - window.innerHeight;
			const progress = travel > 0 ? clamp01(-rect.top / travel) : 0;

			// Linear, not eased: an ease-out would finish the turn while the lid is
			// still shut and the deck would never show as a surface — it would
			// go straight from hidden to edge-on. Linear keeps the skew alive
			// while the lid swings up, so the deck has its moment mid-turn.
			const settle = clamp01(progress / SETTLE_END);
			const open = easeOut(clamp01((progress - OPEN_START) / (OPEN_END - OPEN_START)));
			const walk = clamp01((progress - STEPS_START) / (1 - STEPS_START));

			// Skewed at first, frontal at rest: the yaw and pitch settle out while
			// the lid swings up, so the laptop turns to face you as it opens.
			// (The base is only a thickness strip, so no angle ever shows keys.)
			device.style.setProperty('--ry', `${(-20 + 20 * settle).toFixed(2)}deg`);
			device.style.setProperty('--rx', `${(26 - 24 * settle).toFixed(2)}deg`);
			device.style.setProperty('--sc', (0.88 + 0.12 * settle).toFixed(3));
			device.style.setProperty('--ty', `${(-6 + 6 * settle).toFixed(2)}%`);
			device.style.setProperty('--tx', `${(5 - 5 * settle).toFixed(2)}%`);
			device.style.setProperty('--lid', `${(-60 + 61 * open).toFixed(2)}deg`);
			// The display wakes only once the lid is most of the way up, so the first
			// beat is a dark slab turning into place rather than a lit screen sliding.
			device.style.setProperty('--wake', clamp01((open - 0.25) / 0.45).toFixed(2));
			device.style.setProperty('--shadow-opacity', (0.2 + 0.3 * settle).toFixed(2));

			const index =
				progress < STEPS_START
					? 0
					: Math.min(steps.length - 1, Math.floor(walk * steps.length));
			if (index !== step) step = index;
		};

		window.addEventListener('scroll', onScroll, { passive: true });
		window.addEventListener('resize', onScroll);
		paint();

		// Fonts change the pin's height; re-measure once they land.
		void document.fonts?.ready.then(() => {
			if (!disposed) paint();
		});

		return () => {
			disposed = true;
			if (frame) cancelAnimationFrame(frame);
			window.removeEventListener('scroll', onScroll);
			window.removeEventListener('resize', onScroll);
		};
	});
</script>

<section class="tour" id="tour" data-step={step}>
	<div class="container tour__head" use:reveal={{ from: 'up' }} data-reveal="up">
		<p class="eyebrow">{tour.eyebrow}</p>
		<h2 class="display h2">{tour.title}</h2>
		<p class="lead">{tour.lead}</p>
	</div>

	<div class="tour__pin" bind:this={pinEl} style:--steps={steps.length}>
		<div class="tour__stage">
			<div class="container tour__grid">
				<div class="tour__visual">
					<div class="tour__device" bind:this={deviceEl}>
						<TourDevice state={steps[step].id} />
					</div>
				</div>

				<ol class="tour__steps">
					{#each steps as item, index (item.id)}
						<li class="tour__step" class:active={index === step}>
							<span class="tour__meta">
								<span class="mono tour__index">{String(index + 1).padStart(2, '0')} / {total}</span>
								<span class="mono tour__label">{item.label}</span>
							</span>
							<h3 class="display tour__title">{item.title}</h3>
							<p class="tour__body">{item.body}</p>
						</li>
					{/each}
				</ol>

				<div class="tour__foot">
					<ol class="tour__ticks" aria-hidden="true">
						{#each steps as item, index (item.id)}
							<li class:active={index === step} class:past={index < step}></li>
						{/each}
					</ol>
					<p class="mono tour__hint">{tour.hint}</p>
				</div>
			</div>
		</div>
	</div>
</section>

<style>
	.tour {
		position: relative;
		padding-top: clamp(2.5rem, 5vw, 4.5rem);
	}

	.tour__head {
		display: flex;
		flex-direction: column;
		gap: 1.1rem;
		padding-bottom: clamp(2.5rem, 5vw, 4rem);
	}

	.tour__head .h2 {
		max-width: 18ch;
	}

	.tour__head .lead {
		max-width: 52ch;
	}

	/* The pin owns the scroll budget: one viewport for the stage, then roughly
	   half a viewport per state. Shorter than the four sections it replaced. */
	.tour__pin {
		position: relative;
		height: calc(100vh + var(--steps, 7) * 34vh);
	}

	.tour__stage {
		position: sticky;
		top: 0;
		display: flex;
		align-items: center;
		height: 100vh;
		padding-top: var(--header-h);
		overflow: hidden;
	}

	.tour__grid {
		display: grid;
		grid-template-columns: minmax(0, 1.18fr) minmax(0, 0.82fr);
		align-items: center;
		gap: clamp(2rem, 5vw, 5rem);
	}

	.tour__visual {
		position: relative;
		display: flex;
		justify-content: center;
		min-width: 0;
	}

	.tour__device {
		width: 100%;
		/* The device is painted with its own perspective; this wrapper exists so
		   the scroll driver has a stable node to write variables to. */
		transform-style: preserve-3d;
	}

	/* All seven captions share one grid cell: the box is always as tall as the
	   longest, so swapping never shifts the layout. Inactive ones stay in the
	   accessibility tree and in the prerendered HTML — only their pixels go. */
	.tour__steps {
		display: grid;
		align-self: center;
		min-width: 0;
	}

	.tour__step {
		grid-area: 1 / 1;
		display: flex;
		flex-direction: column;
		gap: 0.9rem;
		opacity: 0;
		transform: translateY(16px);
		transition:
			opacity 420ms var(--ease-out),
			transform 420ms var(--ease-out);
		pointer-events: none;
	}

	.tour__step.active {
		opacity: 1;
		transform: none;
	}

	.tour__meta {
		display: flex;
		align-items: center;
		gap: 1rem;
	}

	.tour__index {
		color: var(--ink-3);
	}

	.tour__label {
		display: inline-flex;
		align-items: center;
		gap: 0.5rem;
		color: var(--accent-text);
	}

	.tour__label::before {
		content: '';
		width: 0.55rem;
		height: 0.55rem;
		border-radius: 50%;
		background: var(--accent);
	}

	.tour__title {
		font-size: clamp(1.85rem, 3.4vw, 3rem);
		line-height: 1.02;
		letter-spacing: -0.025em;
		max-width: 15ch;
	}

	.tour__body {
		font-size: var(--fs-lead);
		line-height: 1.5;
		color: var(--text-2);
		max-width: 34ch;
	}

	.tour__foot {
		grid-column: 1 / -1;
		display: flex;
		align-items: center;
		gap: 1.25rem;
		padding-top: 1rem;
	}

	.tour__ticks {
		display: flex;
		gap: 0.4rem;
		align-items: center;
	}

	.tour__ticks li {
		width: 1.5rem;
		height: 3px;
		border-radius: 99px;
		background: var(--line-strong);
		transition:
			background-color 320ms var(--ease-out),
			width 320ms var(--ease-out);
	}

	.tour__ticks li.past {
		background: var(--ink-3);
	}

	.tour__ticks li.active {
		width: 3rem;
		background: var(--accent);
	}

	.tour__hint {
		margin-left: auto;
		color: var(--ink-3);
		opacity: 0.8;
	}

	/* ---- Small screens ---------------------------------------------------- */
	@media (max-width: 900px) {
		:global(html:not(.js)) .tour__device,
		:global(html.reduced-motion) .tour__device {
			position: static;
		}

		.tour__pin {
			height: calc(100dvh + var(--steps, 7) * 32dvh);
		}

		.tour__stage {
			height: 100dvh;
		}

		.tour__grid {
			grid-template-columns: 1fr;
			gap: clamp(1rem, 3vw, 2rem);
			align-content: center;
		}

		.tour__steps {
			order: 2;
		}

		.tour__foot {
			order: 3;
			padding-top: 0;
		}

		.tour__body {
			font-size: 1rem;
			max-width: 42ch;
		}

		.tour__title {
			font-size: clamp(1.5rem, 5.6vw, 2rem);
			max-width: 18ch;
		}

		.tour__hint {
			display: none;
		}
	}

	@media (max-width: 900px) and (max-height: 720px) {
		.tour__title {
			font-size: 1.4rem;
		}

		.tour__body {
			font-size: 0.95rem;
			line-height: 1.45;
		}
	}

	/* ---- Static fallback ---------------------------------------------------
	   No JavaScript, or a visitor who asked for less motion: no sticky stage, no
	   state swapping. The device keeps its final pose and every caption reads as
	   a plain list. */
	:global(html:not(.js)) .tour__pin,
	:global(html.reduced-motion) .tour__pin {
		height: auto;
	}

	:global(html:not(.js)) .tour__stage,
	:global(html.reduced-motion) .tour__stage {
		position: static;
		height: auto;
		padding-top: 0;
	}

	:global(html:not(.js)) .tour__grid,
	:global(html.reduced-motion) .tour__grid {
		align-items: start;
	}

	/* No scrub to follow, so the device parks beside the list instead. */
	:global(html:not(.js)) .tour__device,
	:global(html.reduced-motion) .tour__device {
		position: sticky;
		top: calc(var(--header-h) + 1rem);
	}

	:global(html:not(.js)) .tour__steps,
	:global(html.reduced-motion) .tour__steps {
		display: flex;
		flex-direction: column;
		gap: 2rem;
	}

	:global(html:not(.js)) .tour__step,
	:global(html.reduced-motion) .tour__step {
		opacity: 1;
		transform: none;
	}

	:global(html:not(.js)) .tour__device,
	:global(html.reduced-motion) .tour__device {
		--ry: 0deg;
		--rx: 2deg;
		--sc: 1;
		--ty: 0%;
		--tx: 0%;
		--lid: 1deg;
		--shadow-opacity: 0.5;
	}

	@media (prefers-reduced-motion: reduce) {
		.tour__pin {
			height: auto;
		}

		.tour__stage {
			position: static;
			height: auto;
			padding-top: 0;
		}

		.tour__steps {
			display: flex;
			flex-direction: column;
			gap: 2rem;
		}

		.tour__step {
			opacity: 1;
			transform: none;
		}
	}
</style>
