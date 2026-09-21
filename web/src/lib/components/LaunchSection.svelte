<script lang="ts">
	import Icon from './Icon.svelte';
	import { launch } from '$data/site';
	import { reveal, stagger } from '$lib/actions/reveal';

	type LaunchItemTitle = (typeof launch.items)[number]['title'];

	/**
	 * Glyph per launch card, keyed by the title in `site.ts` so reordering the
	 * copy can never silently swap the icons.
	 */
	const cardIcons: Record<LaunchItemTitle, 'bolt' | 'layers' | 'chevron-down'> = {
		'One click to launch': 'bolt',
		'Always on top': 'layers',
		'Collapse on launch': 'chevron-down'
	};
</script>

<section class="section" id="launch">
	<div class="container">
		<header class="section-head" use:reveal={{ from: 'up' }} data-reveal="up">
			<p class="eyebrow">{launch.eyebrow}</p>
			<h2 class="display h2">{launch.title}</h2>
			<p class="lead">{launch.body}</p>
		</header>

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
	.cards article {
		display: flex;
		flex-direction: column;
		gap: 0.6rem;
	}

	.badge {
		display: inline-grid;
		place-items: center;
		width: 40px;
		height: 40px;
		margin-bottom: 0.3rem;
		border-radius: 12px;
		border: 1px solid rgb(79 70 229 / 0.25);
		background: rgb(79 70 229 / 0.12);
		box-shadow: inset 0 1px 0 rgb(255 255 255 / 0.6);
		color: #4338ca;
	}

	.cards p {
		font-size: var(--fs-small);
		color: var(--text-3);
	}
</style>
