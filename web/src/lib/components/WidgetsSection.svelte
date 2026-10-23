<script lang="ts">
	import NotchWidgetDemo from './NotchWidgetDemo.svelte';
	import Icon from './Icon.svelte';
	import { widgets, type WidgetId } from '$data/site';
	import { reveal, stagger } from '$lib/actions/reveal';

	const widgetIcons: Record<WidgetId, 'apps' | 'cpu' | 'sun' | 'calendar' | 'play'> = {
		apps: 'apps',
		system: 'cpu',
		weather: 'sun',
		calendar: 'calendar',
		music: 'play'
	};
</script>

<section class="section" id="widgets">
	<div class="container">
		<header class="section-head" use:reveal={{ from: 'up' }} data-reveal="up">
			<p class="eyebrow">Widgets</p>
			<h2 class="display h2">A whole dockful of widgets.</h2>
			<p class="lead">
				Scroll up or down inside the notch to hop between your apps, the system
				rings, the weather, the day ahead and your music. No windows, no hunting.
			</p>
		</header>

		<div class="layout">
			<div class="stage" use:reveal={{ from: 'scale', delay: 0.08 }} data-reveal="scale">
				<div class="stage__crop" aria-hidden="true">
					<span class="tape tape--tl"></span>
					<span class="tape tape--br"></span>
				</div>
				<span class="mono stage__tag">fig. 1: the daily scroll</span>
				<NotchWidgetDemo label="Widget demo" />
			</div>

			<ul class="list" use:stagger={{ step: 0.07 }} data-stagger>
				{#each widgets as widget, index (widget.id)}
					<li class="item">
						<span class="item__no mono">0{index + 1}</span>
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

			<p class="teaser" aria-label="More widgets are planned">
				<Icon name="sparkle" size={15} />
				More widgets are in the oven.
			</p>
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
		position: relative;
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 0.9rem;
	}

	/* Crop marks with washi tape hold the product shot against the page. */
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
		left: 12%;
	}

	.tape--br {
		right: 12%;
		bottom: 3.2rem;
		rotate: 3deg;
	}

	.stage__tag {
		z-index: 1;
		color: var(--ink-3);
		opacity: 0.85;
	}

	.list {
		position: relative;
		z-index: 1;
		display: flex;
		flex-direction: column;
		gap: 0.75rem;
	}

	.item {
		display: flex;
		align-items: center;
		gap: 1rem;
		padding: 0.85rem 1.1rem;
		border-radius: var(--r-md);
		border: 2px solid var(--ink);
		background: var(--paper-lift);
		box-shadow: 3px 3px 0 rgb(33 28 19 / 0.85);
		transition: transform var(--dur-fast) var(--ease-out), box-shadow var(--dur) var(--ease-out);
	}

	.item:hover {
		transform: translate(-1px, -1px);
		box-shadow: 4px 4px 0 rgb(33 28 19 / 0.85);
	}

	.item__no {
		flex: none;
		color: var(--ink-3);
	}

	.item__icon {
		display: grid;
		place-items: center;
		flex: none;
		width: 40px;
		height: 40px;
		border-radius: var(--r-sm);
		border: 2px solid var(--ink);
		background: var(--accent-soft);
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

	/* Footnote ticket under the widget list — a glimpse of what is still coming. */
	.teaser {
		position: relative;
		z-index: 1;
		display: inline-flex;
		align-items: center;
		justify-content: center;
		gap: 0.5rem;
		width: fit-content;
		margin-top: 0.9rem;
		padding: 0.7rem 1.1rem;
		border: 2px dashed var(--line-strong);
		border-radius: var(--r-md);
		font-size: var(--fs-small);
		color: var(--text-3);
	}

	.teaser :global(svg) {
		color: var(--accent);
		flex: none;
	}

	@media (min-width: 900px) {
		.layout {
			grid-template-columns: minmax(0, 1.05fr) minmax(0, 0.95fr);
		}
	}
</style>
