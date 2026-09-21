<script lang="ts">
	import NotchWidgetDemo from './NotchWidgetDemo.svelte';
	import Icon from './Icon.svelte';
	import { widgets, type WidgetId } from '$data/site';
	import { reveal, stagger } from '$lib/actions/reveal';

	const widgetIcons: Record<WidgetId, 'apps' | 'sun' | 'cpu' | 'bolt' | 'play'> = {
		apps: 'apps',
		weather: 'sun',
		system: 'cpu',
		fps: 'bolt',
		music: 'play'
	};
</script>

<section class="section" id="widgets">
	<div class="container">
		<header class="section-head" use:reveal={{ from: 'up' }} data-reveal="up">
			<p class="eyebrow">Widgets</p>
			<h2 class="display h2">Everything, one scroll away.</h2>
			<p class="lead">
				The notch is more than a launcher. Scroll up or down inside it to move between
				glanceable widgets — no windows, no hunting.
			</p>
		</header>

		<div class="layout">
			<div class="stage" use:reveal={{ from: 'scale', delay: 0.08 }} data-reveal="scale">
				<NotchWidgetDemo label="Widget demo" />
			</div>

			<ul class="list" use:stagger={{ step: 0.07 }} data-stagger>
				{#each widgets as widget (widget.id)}
					<li class="item">
						<span class="item__icon">
							<Icon name={widgetIcons[widget.id]} size={18} />
						</span>
						<span class="item__text">
							<strong>{widget.label}</strong>
							<span>{widget.blurb}</span>
						</span>
					</li>
				{/each}
			</ul>
		</div>
	</div>
</section>

<style>
	.layout {
		display: grid;
		gap: clamp(2rem, 4vw, 3.5rem);
		align-items: center;
	}

	.stage {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 1.1rem;
	}

	.list {
		display: flex;
		flex-direction: column;
		gap: 0.5rem;
	}

	.item {
		display: flex;
		align-items: center;
		gap: 1rem;
		padding: 1rem 1.1rem;
		border-radius: var(--r-md);
		border: 1px solid var(--line);
		background: rgb(255 255 255 / 0.6);
	}

	.item__icon {
		display: grid;
		place-items: center;
		flex: none;
		width: 40px;
		height: 40px;
		border-radius: 12px;
		border: 1px solid rgb(79 70 229 / 0.25);
		background: rgb(79 70 229 / 0.1);
		color: var(--accent-text);
	}

	.item__text {
		display: flex;
		flex-direction: column;
		gap: 0.15rem;
	}

	.item__text strong {
		font-family: var(--font-display);
		font-size: 1rem;
		color: var(--text);
	}

	.item__text span {
		font-size: var(--fs-small);
		color: var(--text-3);
	}

	@media (min-width: 900px) {
		.layout {
			grid-template-columns: minmax(0, 1.05fr) minmax(0, 0.95fr);
		}
	}
</style>
