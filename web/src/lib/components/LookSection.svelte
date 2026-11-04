<script lang="ts">
	import { look, surfaces, themes } from '$data/site';
	import { reveal, stagger } from '$lib/actions/reveal';

	/**
	 * The look: three profiles, two finishes, one row.
	 *
	 * The old section spent three tall cards and three more rows on this — a
	 * screen of scrolling for two dropdowns. A swatch plus one line each says the
	 * same thing, and the tour above already shows the real shapes at scale.
	 */
</script>

<section class="section look" id="look">
	<div class="container">
		<header class="look__head" use:reveal={{ from: 'up' }} data-reveal="up">
			<div>
				<p class="eyebrow">{look.eyebrow}</p>
				<h2 class="display h2">{look.title}</h2>
			</div>
			<p class="look__lead">{look.lead}</p>
		</header>

		<ul class="look__row" use:stagger={{ step: 0.06 }} data-stagger>
			{#each themes as theme, index (theme.id)}
				<li class="look__item">
					<span class="look__swatch look__swatch--screen" aria-hidden="true">
						<span class="look__ref mono">{look.shapeRef} {index + 1}</span>
						<span class="look__notch" data-style={theme.id}></span>
					</span>
					<span class="look__text">
						<strong>{theme.label}</strong>
						<span>{theme.blurb}</span>
					</span>
				</li>
			{/each}

			{#each surfaces as surface (surface.id)}
				<li class="look__item">
					<span class="look__swatch look__swatch--paper" aria-hidden="true">
						<span class="look__ref mono">{look.finishRef}</span>
						<span class="look__pill" data-surface={surface.id}></span>
					</span>
					<span class="look__text">
						<strong>{surface.label}</strong>
						<span>{surface.blurb}</span>
					</span>
				</li>
			{/each}
		</ul>
	</div>
</section>

<style>
	.look {
		padding-block: clamp(2rem, 4vw, 3rem) clamp(1.5rem, 3vw, 2.5rem);
	}

	.look__head {
		display: flex;
		flex-wrap: wrap;
		align-items: end;
		justify-content: space-between;
		gap: 1.5rem;
		margin-bottom: clamp(1.5rem, 3vw, 2.25rem);
	}

	.look__head .h2 {
		margin-top: 0.6rem;
	}

	.look__lead {
		max-width: 32ch;
		font-size: var(--fs-small);
		line-height: 1.55;
		color: var(--text-3);
	}

	.look__row {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(min(100%, 12rem), 1fr));
		gap: clamp(0.75rem, 1.4vw, 1.1rem);
	}

	.look__item {
		display: flex;
		flex-direction: column;
		gap: 0.7rem;
		padding: 0.7rem;
		border-radius: var(--r-md);
		border: 2px solid var(--ink);
		background: var(--paper-lift);
		box-shadow: 3px 3px 0 rgb(33 28 19 / 0.85);
	}

	.look__swatch {
		position: relative;
		display: block;
		height: 64px;
		border-radius: 8px;
		overflow: hidden;
	}

	.look__ref {
		position: absolute;
		left: 0.55rem;
		bottom: 0.45rem;
		font-size: 8px;
		letter-spacing: 0.14em;
		color: rgb(245 242 234 / 0.45);
	}

	/* Three profiles, drawn as a cut-out on a dark screen. */
	.look__swatch--screen {
		border: 1px solid rgb(33 28 19 / 0.5);
		background: #16120c;
	}

	.look__notch {
		position: absolute;
		top: 12px;
		left: 50%;
		translate: -50% 0;
		transition: background-color var(--dur) var(--ease-out);
	}

	.look__notch[data-style='amoled'] {
		top: 0;
		width: 52%;
		height: 18px;
		border-radius: 0 0 9px 9px;
		background: #000;
		border: 1px solid rgb(245 242 234 / 0.7);
		border-top: none;
	}

	.look__notch[data-style='pill'] {
		top: 15px;
		width: 44%;
		height: 15px;
		border-radius: 999px;
		background: rgb(10 10 10 / 0.92);
		border: 1px solid rgb(245 242 234 / 0.7);
	}

	.look__notch[data-style='handle'] {
		top: 17px;
		width: 24%;
		height: 8px;
		border-radius: 999px;
		border: 1px dashed rgb(245 242 234 / 0.65);
		background: rgb(255 255 255 / 0.16);
	}

	/* Two finishes over the same wallpaper, so the difference is visible: solid
	   hides it, frost lets it blur through. Flat shapes, no gradient — the
	   blur itself is the point. */
	.look__swatch--paper {
		border: 1px solid rgb(33 28 19 / 0.35);
		background: #f1e4cf;
	}

	.look__swatch--paper::before,
	.look__swatch--paper::after {
		content: '';
		position: absolute;
	}

	.look__swatch--paper::before {
		left: 4%;
		top: 0;
		width: 38%;
		height: 100%;
		background: rgb(31 79 216 / 0.5);
	}

	.look__swatch--paper::after {
		right: 4%;
		bottom: 0;
		width: 30%;
		height: 62%;
		background: rgb(232 72 28 / 0.5);
	}

	.look__pill {
		position: absolute;
		z-index: 1;
		top: 0;
		left: 50%;
		translate: -50% 0;
		width: 56px;
		height: 22px;
		border-radius: 0 0 11px 11px;
		border: 1px solid rgb(255 255 255 / 0.2);
		border-top: none;
	}

	.look__pill[data-surface='solid'] {
		background: #0a0a0a;
	}

	.look__pill[data-surface='frost'] {
		background: rgb(255 255 255 / 0.18);
		backdrop-filter: blur(5px) saturate(150%);
		-webkit-backdrop-filter: blur(5px) saturate(150%);
	}

	.look__text {
		display: flex;
		flex-direction: column;
		gap: 0.2rem;
		min-width: 0;
	}

	.look__text strong {
		font-family: var(--font-display);
		font-size: 0.98rem;
		color: var(--text);
	}

	.look__text > span {
		font-size: 0.82rem;
		line-height: 1.45;
		color: var(--text-3);
	}
</style>
