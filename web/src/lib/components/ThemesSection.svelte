<script lang="ts">
	import { themes } from '$data/site';
	import { reveal, stagger } from '$lib/actions/reveal';
</script>

<section class="section" id="themes">
	<div class="container">
		<header class="section-head" use:reveal={{ from: 'up' }} data-reveal="up">
			<p class="eyebrow">Theming</p>
			<h2 class="display h2">The notch, your way.</h2>
			<p class="lead">
				Pick the look that fits how you work — and switch any time.
			</p>
		</header>

		<div class="grid" use:stagger={{ step: 0.09 }} data-stagger>
			{#each themes as theme (theme.id)}
				<article class="card card--lift theme">
					<div class="theme__screen" aria-hidden="true">
						<span class="tick tick--tl"></span>
						<span class="tick tick--tr"></span>
						<span class="tick tick--bl"></span>
						<span class="tick tick--br"></span>

						<span class="theme__notch" data-style={theme.id}></span>
					</div>
					<div class="theme__text">
						<h3 class="h4">{theme.label}</h3>
						<p>{theme.blurb}</p>
					</div>
				</article>
			{/each}
		</div>
	</div>
</section>

<style>
	.grid {
		display: grid;
		gap: clamp(1rem, 2vw, 1.6rem);
		grid-template-columns: repeat(auto-fit, minmax(min(100%, 16rem), 1fr));
	}

	.theme {
		display: flex;
		flex-direction: column;
		gap: 1.25rem;
	}

	/* ---- Sketch canvas ---------------------------------------------------- */
	.theme__screen {
		position: relative;
		height: 190px;
		border-radius: var(--r-md);
		/* A dashed frame reads as a technical drawing — the "sketch" look. */
		border: 1.5px dashed rgb(148 163 184 / 0.5);
		background: rgb(8 11 18 / 0.55);
		overflow: hidden;
	}

	/* Corner registration marks, like a blueprint crop. */
	.tick {
		position: absolute;
		width: 11px;
		height: 11px;
		border: 1.5px solid var(--indigo-light);
		opacity: 0.75;
	}

	.tick--tl {
		top: 8px;
		left: 8px;
		border-right: none;
		border-bottom: none;
	}

	.tick--tr {
		top: 8px;
		right: 8px;
		border-left: none;
		border-bottom: none;
	}

	.tick--bl {
		bottom: 8px;
		left: 8px;
		border-right: none;
		border-top: none;
	}

	.tick--br {
		bottom: 8px;
		right: 8px;
		border-left: none;
		border-top: none;
	}

	.theme__notch {
		position: absolute;
		left: 50%;
		translate: -50% 0;
		transition: border-color var(--dur) var(--ease-out), outline-color var(--dur) var(--ease-out),
			background-color var(--dur) var(--ease-out);
	}

	.theme:hover .theme__notch {
		outline-color: var(--cyan);
	}

	/* AMOLED notch — a deep-black cutout sitting flush on the top edge. */
	.theme__notch[data-style='amoled'] {
		top: 26px;
		width: 58%;
		height: 34px;
		border-radius: 0 0 17px 17px;
		background: #000;
		border: 1.5px solid rgb(226 232 240 / 0.7);
		outline: 1.5px dashed var(--indigo-light);
		outline-offset: 6px;
	}

	.theme__notch[data-style='amoled']::after {
		content: '';
		position: absolute;
		top: 9px;
		right: 12px;
		width: 9px;
		height: 9px;
		border-radius: 50%;
		background: #0b0f19;
		border: 1px solid rgb(255 255 255 / 0.35);
	}

	/* Floating pill — soft, rounded, sitting just off the edge. */
	.theme__notch[data-style='pill'] {
		top: 30px;
		width: 46%;
		height: 30px;
		border-radius: 999px;
		background: rgb(10 10 10 / 0.9);
		border: 1.5px solid rgb(226 232 240 / 0.7);
		outline: 1.5px dashed var(--indigo-light);
		outline-offset: 6px;
		box-shadow: 0 16px 30px -14px rgb(0 0 0 / 0.9);
	}

	/* Drag handle — slim and quiet, a grip you reach for. */
	.theme__notch[data-style='handle'] {
		top: 32px;
		width: 26%;
		height: 12px;
		border-radius: 999px;
		background: rgb(255 255 255 / 0.16);
		border: 1.5px dashed rgb(226 232 240 / 0.65);
		outline: 1.5px solid var(--indigo-light);
		outline-offset: 6px;
	}

	.theme__notch[data-style='handle']::after {
		content: '';
		position: absolute;
		left: 50%;
		top: 50%;
		translate: -50% -50%;
		width: 4px;
		height: 4px;
		border-radius: 50%;
		background: rgb(255 255 255 / 0.55);
	}

	.theme__text {
		display: flex;
		flex-direction: column;
		gap: 0.4rem;
	}

	.theme__text p {
		font-size: var(--fs-small);
		color: var(--text-3);
	}
</style>
