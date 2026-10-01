<script lang="ts">
	import Icon from './Icon.svelte';
	import DockDemo from './DockDemo.svelte';
	import { launch } from '$data/site';
	import { reveal, stagger } from '$lib/actions/reveal';

	type LaunchItemTitle = (typeof launch.items)[number]['title'];

	/**
	 * Glyph per launch card, keyed by the title in `site.ts` so reordering the
	 * copy can never silently swap the icons.
	 */
	const cardIcons: Record<LaunchItemTitle, 'bolt' | 'layers' | 'chevron-down' | 'arrow-up-right'> = {
		'Hover to expand': 'chevron-down',
		'Always on top': 'layers',
		'Tucks away on its own': 'arrow-up-right'
	};
</script>

<section class="section" id="launch">
	<div class="container">
		<header class="section-head" use:reveal={{ from: 'up' }} data-reveal="up">
			<p class="eyebrow">{launch.eyebrow}</p>
			<h2 class="display h2">{launch.title}</h2>
			<p class="lead">{launch.body}</p>
		</header>

		<div class="stage" use:reveal={{ from: 'scale', delay: 0.05 }} data-reveal="scale">
			<div class="stage__crop" aria-hidden="true">
				<span class="tape tape--tl"></span>
				<span class="tape tape--br"></span>
			</div>
			<span class="mono stage__tag">fig. 2: hover the notch to open the overlay</span>
			<DockDemo />
		</div>

		<div class="grid-2 cards" use:stagger={{ step: 0.08 }} data-stagger>
			{#each launch.items as item (item.title)}
				<article class="card card--lift">
					<span class="badge">
						<Icon name={cardIcons[item.title]} size={19} />
					</span>
					<h3 class="h4">{item.title}</h3>
					<p>{item.body}</p>
				</article>
			{/each}
		</div>
	</div>
</section>

<style>
	.stage {
		position: relative;
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 0.9rem;
		margin-bottom: clamp(2.5rem, 5vw, 4rem);
	}

	.stage__crop {
		position: absolute;
		inset: -0.4rem 10%;
		border: 2px dashed var(--line-strong);
		border-radius: var(--r-lg);
		pointer-events: none;
		z-index: 0;
	}

	.tape--tl {
		top: -0.8rem;
		left: 14%;
	}

	.tape--br {
		right: 14%;
		bottom: 2rem;
		rotate: 3deg;
	}

	.stage__tag {
		z-index: 1;
		color: var(--ink-3);
		opacity: 0.85;
	}

	.cards {
		position: relative;
		z-index: 1;
	}

	.cards article {
		display: flex;
		flex-direction: column;
		gap: 0.6rem;
	}

	.badge {
		display: inline-grid;
		place-items: center;
		width: 42px;
		height: 42px;
		margin-bottom: 0.3rem;
		border-radius: var(--r-sm);
		border: 2px solid var(--ink);
		background: var(--accent-soft);
		box-shadow: 2px 2px 0 rgb(33 28 19 / 0.75);
		color: var(--accent-text);
	}

	.cards p {
		font-size: var(--fs-small);
		color: var(--text-3);
	}
</style>