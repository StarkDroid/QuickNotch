<script lang="ts">
	import { gaming } from '$data/site';
	import { reveal, stagger } from '$lib/actions/reveal';

	/**
	 * The gaming quickview, shown as the two states it actually has.
	 *
	 * Both frames are the same notch with the same five readouts; the only
	 * difference is the body — solid (whatever surface you picked) or the
	 * "transparent in games" repaint, which drops the fill and keeps a thin rim
	 * plus a soft shadow. The scene behind the second frame is a stand-in for a
	 * game, because transparency over blank paper would demonstrate nothing.
	 *
	 * The numbers are real text, not an image, so the strip is crawlable and
	 * readable to assistive tech; the scenes are pure CSS decoration.
	 */
</script>

<section class="section" id="gaming">
	<div class="container">
		<header class="section-head" use:reveal={{ from: 'up' }} data-reveal="up">
			<p class="eyebrow">{gaming.eyebrow}</p>
			<h2 class="display h2">{gaming.title}</h2>
			<p class="lead">{gaming.body}</p>
		</header>

		<p class="mono modes__tag">fig. 3: the same strip, two bodies</p>

		<div class="modes" use:stagger={{ step: 0.1 }} data-stagger>
			{#each gaming.modes as mode (mode.id)}
				<figure class="mode">
					<div class="mode__screen" data-mode={mode.id}>
						<span class="mode__glow mode__glow--a" aria-hidden="true"></span>
						<span class="mode__glow mode__glow--b" aria-hidden="true"></span>
						<span class="mode__scene-tag mono" aria-hidden="true">the game</span>
						<span class="mode__notch">
							{#each gaming.cells as cell, index (cell.label)}
								{#if index > 0}
									<span class="mode__divider" aria-hidden="true"></span>
								{/if}
								<span class="mode__cell">
									<strong>{cell.value}</strong>
									<span class="mono">{cell.label}</span>
								</span>
							{/each}
						</span>
					</div>
					<figcaption>
						<strong>{mode.label}</strong>
						<span>{mode.blurb}</span>
					</figcaption>
				</figure>
			{/each}
		</div>

		<div class="grid-3 notes" use:stagger={{ step: 0.08 }} data-stagger>
			{#each gaming.notes as note (note.title)}
				<article class="card card--lift">
					<h3 class="h4">{note.title}</h3>
					<p>{note.body}</p>
				</article>
			{/each}
		</div>
	</div>
</section>

<style>
	.modes {
		display: grid;
		gap: clamp(1rem, 2vw, 1.6rem);
		grid-template-columns: repeat(auto-fit, minmax(min(100%, 20rem), 1fr));
		margin-bottom: clamp(1.5rem, 3vw, 2.4rem);
	}

	.modes__tag {
		margin-bottom: 0.7rem;
		color: var(--ink-3);
		opacity: 0.85;
	}

	.mode {
		display: flex;
		flex-direction: column;
		gap: 0.85rem;
	}

	/* The game, as a frame: dark, rounded, with the notch parked on its top
	   edge — the same place the real island lives. Two blurred blobs stand in
	   for a scene; painted gradients are not this site's language. */
	.mode__screen {
		position: relative;
		display: flex;
		justify-content: center;
		align-items: flex-start;
		height: 148px;
		padding-top: 0.9rem;
		border-radius: var(--r-md);
		border: 2px solid var(--ink);
		box-shadow: 3px 3px 0 rgb(33 28 19 / 0.85);
		overflow: hidden;
		background: #0e141d;
	}

	.mode__glow {
		position: absolute;
		border-radius: 50%;
		filter: blur(28px);
		opacity: 0.55;
		pointer-events: none;
	}

	.mode__glow--a {
		top: -34%;
		left: 12%;
		width: 62%;
		height: 130%;
		background: #2a6f8f;
	}

	.mode__glow--b {
		right: -10%;
		bottom: -40%;
		width: 56%;
		height: 115%;
		background: #b3451f;
	}

	/* Names the abstract backdrop, so a blurred wash reads as "the game". */
	.mode__scene-tag {
		position: absolute;
		top: 0.45rem;
		left: 0.7rem;
		font-size: 8px;
		letter-spacing: 0.14em;
		color: rgb(245 242 234 / 0.35);
		pointer-events: none;
	}

	/* Transparent in games: the game shows through the notch body, so the frame
	   behind it gets to be busy — that is the whole point of the mode. */
	.mode__screen[data-mode='clear'] .mode__glow {
		opacity: 0.9;
	}

	.mode__notch {
		position: relative;
		z-index: 1;
		display: flex;
		align-items: center;
		gap: 0.1rem;
		width: min(100% - 1.4rem, 320px);
		padding: 0.5rem 0.7rem;
		border-radius: 14px;
		border: 1px solid var(--on-dark-line-strong);
		background: rgb(10 10 10 / 0.92);
		box-shadow: var(--shadow-2);
	}

	.mode__screen[data-mode='clear'] .mode__notch {
		border-color: rgb(0 0 0 / 0.6);
		background: rgb(8 10 14 / 0.06);
		box-shadow: 0 10px 24px rgb(0 0 0 / 0.5);
	}

	.mode__cell {
		display: flex;
		flex-direction: column;
		align-items: center;
		flex: 1 1 0;
		min-width: 0;
		line-height: 1.15;
	}

	/* Values keep their own case and spacing: `ms`, `M/s` are units, not caps. */
	.mode__cell strong {
		font-family: var(--font-mono);
		font-size: 0.82rem;
		font-weight: 600;
		font-variant-numeric: tabular-nums;
		letter-spacing: 0;
		white-space: nowrap;
		color: var(--on-dark);
	}

	.mode__cell span {
		font-size: 7.5px;
		letter-spacing: 0.12em;
		white-space: nowrap;
		color: var(--on-dark-3);
	}

	.mode__divider {
		flex: none;
		width: 1px;
		height: 16px;
		background: rgb(255 255 255 / 0.12);
	}

	.mode__screen[data-mode='clear'] .mode__divider {
		background: rgb(0 0 0 / 0.5);
	}

	/* Only the body-less mode needs a shadow behind its glyphs. */
	.mode__screen[data-mode='clear'] .mode__cell {
		text-shadow: 0 1px 4px rgb(0 0 0 / 0.85);
	}

	figcaption {
		display: flex;
		flex-direction: column;
		gap: 0.2rem;
	}

	figcaption strong {
		font-family: var(--font-display);
		font-size: 1rem;
		color: var(--text);
	}

	figcaption span {
		font-size: var(--fs-small);
		color: var(--text-3);
	}

	.notes :global(p) {
		margin-top: 0.5rem;
	}
</style>
