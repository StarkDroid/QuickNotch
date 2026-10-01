<script lang="ts">
	import AppTile from './AppTile.svelte';
	import Icon from './Icon.svelte';
	import { dockTiles, widgets } from '$data/site';
	import { prefersReducedMotion } from '$lib/motion/gsap';

	/**
	 * The glanceable notch, showing one widget at a time.
	 *
	 * "Vertical scroll pagination": scrolling over the notch (or using the left /
	 * right arrows below) moves through the widget stack — quick launch, weather,
	 * system usage, game FPS and live activities. Each switch slides the next
	 * widget into view.
	 *
	 * The notch is a real control: prev/next and the dots are buttons, and the
	 * current widget is announced via `aria-label`. Autoplay pauses for a few
	 * seconds after any interaction.
	 */

	interface Props {
		/** Visual style of the notch container. */
		theme?: 'amoled' | 'pill' | 'handle';
		/** Auto-advance through the widgets when idle. */
		autoplay?: boolean;
		/** Allow wheel / pointer pagination (display-only demos disable it). */
		interactive?: boolean;
		label?: string;
	}

	let {
		theme = 'pill',
		autoplay = true,
		interactive = true,
		label = 'Notch widgets'
	}: Props = $props();

	let active = $state(0);
	let pausedUntil = 0;
	let reduced = false;

	const count = widgets.length;
	const current = $derived(widgets[active]);

	function go(delta: number) {
		active = (active + delta + count) % count;
		pausedUntil = Date.now() + 6000;
	}

	function onWheel(event: WheelEvent) {
		if (!interactive) return;
		event.preventDefault();
		if (Math.abs(event.deltaY) < 24) return;
		go(event.deltaY > 0 ? 1 : -1);
	}

	/** Wheel listeners are passive by default in some browsers, so preventDefault
	 *  needs an explicit non-passive listener. */
	function wheelPager(node: HTMLElement) {
		node.addEventListener('wheel', onWheel, { passive: false });
		return {
			destroy() {
				node.removeEventListener('wheel', onWheel);
			}
		};
	}

	$effect(() => {
		reduced = prefersReducedMotion();
	});

	$effect(() => {
		if (!autoplay || reduced) return;
		const id = setInterval(() => {
			if (Date.now() < pausedUntil) return;
			active = (active + 1) % count;
		}, 3200);
		return () => clearInterval(id);
	});
</script>

<div class="notch-wrap">
	<div
		class="notch"
		class:notch--amoled={theme === 'amoled'}
		class:notch--handle={theme === 'handle'}
		use:wheelPager
		role="region"
		aria-label="{label} — {current.label}"
	>
		<span class="notch__title mono" aria-hidden="true">{current.label}</span>

		<div class="notch__body">
			{#key active}
				<div class="widget">
					{#if current.id === 'apps'}
						<div class="widget__apps">
							{#each dockTiles.slice(0, 5) as tile (tile.id)}
								<AppTile {tile} size={40} decorative />
							{/each}
						</div>
					{:else if current.id === 'weather'}
						<div class="widget__weather">
							<span class="weather__icon"><Icon name="sun" size={30} /></span>
							<span class="weather__main">
								<strong>23°</strong>
								<span>Partly cloudy</span>
							</span>
							<span class="weather__forecast">
								<i>Tue <b>21°</b></i>
								<i>Wed <b>19°</b></i>
								<i>Thu <b>17°</b></i>
							</span>
						</div>
					{:else if current.id === 'system'}
						<div class="widget__system">
							{#each [
								['CPU', 34],
								['RAM', 61],
								['GPU', 12],
								['NET', 8]
							] as metric (metric[0])}
								<span class="meter">
									<span class="meter__label mono">{metric[0]}</span>
									<span class="meter__track"><i style:width="{metric[1]}%"></i></span>
									<span class="meter__value">{metric[1]}</span>
								</span>
							{/each}
						</div>
					{:else if current.id === 'fps'}
						<div class="widget__fps">
							<span class="fps__number"><strong>144</strong> FPS</span>
							<span class="fps__graph" aria-hidden="true">
								{#each [40, 58, 46, 70, 62, 78, 68, 88, 74, 92] as h (h)}
									<i style:height="{h}%"></i>
								{/each}
							</span>
							<span class="fps__tag mono">Game mode</span>
						</div>
					{:else if current.id === 'music'}
						<div class="widget__music">
							<AppTile tile={dockTiles.find((t) => t.id === 'music')!} size={44} decorative />
							<span class="music__meta">
								<strong>Midnight City</strong>
								<span>M83</span>
							</span>
							<span class="music__controls">
								<button type="button" aria-label="Previous track"><Icon name="next" size={15} class="flip" /></button>
								<button type="button" aria-label="Play" class="music__play"><Icon name="play" size={15} /></button>
								<button type="button" aria-label="Next track"><Icon name="next" size={15} /></button>
							</span>
						</div>
					{/if}
				</div>
			{/key}
		</div>
	</div>

	<div class="notch__pager">
		<button type="button" class="pager__arrow" onclick={() => go(-1)} aria-label="Previous widget">
			<Icon name="arrow-left" size={16} />
		</button>

		<div class="pager__dots" role="tablist" aria-label="Widgets">
			{#each widgets as widget, index (widget.id)}
				<button
					type="button"
					class="pager__dot"
					class:active={active === index}
					role="tab"
					aria-selected={active === index}
					aria-label={widget.label}
					title={widget.label}
					onclick={() => {
						active = index;
						pausedUntil = Date.now() + 6000;
					}}
				></button>
			{/each}
		</div>

		<button type="button" class="pager__arrow" onclick={() => go(1)} aria-label="Next widget">
			<Icon name="arrow-right" size={16} />
		</button>
	</div>
</div>

<style>
	.notch-wrap {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 0.9rem;
	}

	/* ---- The notch container --------------------------------------------- */
	.notch {
		position: relative;
		width: min(92vw, 380px);
		min-height: 92px;
		padding: 1.6rem 1.15rem 0.95rem;
		border-radius: 22px;
		border: 1px solid var(--on-dark-line-strong);
		background: rgb(10 10 10 / 0.92);
		backdrop-filter: blur(18px) saturate(140%);
		-webkit-backdrop-filter: blur(18px) saturate(140%);
		box-shadow: var(--shadow-2);
		overflow: hidden;
		transition: background-color var(--dur) var(--ease-out), border-color var(--dur) var(--ease-out);
	}

	/* AMOLED: deep black, flush, no chrome — it reads as part of the screen. */
	.notch--amoled {
		border-color: transparent;
		background: #000;
		box-shadow: inset 0 0 0 1px rgb(255 255 255 / 0.04);
	}

	/* Drag handle: slimmer and quieter. */
	.notch--handle {
		min-height: 78px;
		padding-top: 1.25rem;
		border-radius: 16px;
		border-color: rgb(255 255 255 / 0.08);
		background: rgb(10 10 10 / 0.55);
		box-shadow: none;
	}

	.notch__title {
		position: absolute;
		top: 0.5rem;
		left: 1rem;
		font-size: 9px;
		color: var(--on-dark-3);
	}

	.notch__body {
		display: grid;
		place-items: center;
		min-height: 46px;
	}

	.widget {
		width: 100%;
		animation: widget-in 380ms var(--ease-out);
	}

	@keyframes widget-in {
		from {
			opacity: 0;
			transform: translateY(16px);
		}
		to {
			opacity: 1;
			transform: none;
		}
	}

	/* ---- Widget layouts --------------------------------------------------- */
	.widget__apps {
		display: flex;
		align-items: center;
		justify-content: center;
		gap: 0.5rem;
	}

	.widget__weather {
		display: flex;
		align-items: center;
		gap: 1rem;
	}

	.weather__icon {
		display: grid;
		place-items: center;
		width: 46px;
		height: 46px;
		border-radius: 14px;
		background: rgb(251 191 36 / 0.14);
		color: #fbbf24;
		flex: none;
	}

	.weather__main {
		display: flex;
		flex-direction: column;
		line-height: 1.05;
	}

	.weather__main strong {
		font-family: var(--font-display);
		font-size: 1.7rem;
		letter-spacing: -0.03em;
		color: var(--on-dark);
	}

	.weather__main span {
		font-size: var(--fs-micro);
		color: var(--on-dark-3);
	}

	.weather__forecast {
		display: flex;
		gap: 0.9rem;
		margin-left: auto;
	}

	.weather__forecast i {
		display: flex;
		flex-direction: column;
		align-items: center;
		font-style: normal;
		font-size: 10px;
		color: var(--on-dark-3);
	}

	.weather__forecast b {
		font-weight: 600;
		color: var(--on-dark-2);
	}

	.widget__system {
		display: grid;
		grid-template-columns: 1fr 1fr;
		gap: 0.5rem 1rem;
		width: 100%;
	}

	.meter {
		display: grid;
		grid-template-columns: 2.2rem 1fr 1.6rem;
		align-items: center;
		gap: 0.5rem;
	}

	.meter__label {
		font-size: 9px;
		color: var(--on-dark-3);
	}

	.meter__track {
		height: 4px;
		border-radius: 99px;
		background: rgb(255 255 255 / 0.1);
		overflow: hidden;
	}

	.meter__track i {
		display: block;
		height: 100%;
		border-radius: 99px;
		background: var(--accent);
	}

	.meter__value {
		font-size: 10px;
		font-variant-numeric: tabular-nums;
		color: var(--on-dark-2);
		text-align: right;
	}

	.widget__fps {
		display: flex;
		align-items: center;
		gap: 1rem;
	}

	.fps__number {
		display: flex;
		flex-direction: column;
		align-items: flex-start;
		line-height: 1;
		font-family: var(--font-mono);
		font-size: 11px;
		letter-spacing: 0.1em;
		color: var(--on-dark-3);
		text-transform: uppercase;
	}

	.fps__number strong {
		font-family: var(--font-display);
		font-size: 2rem;
		letter-spacing: -0.03em;
		color: var(--green);
	}

	.fps__graph {
		display: flex;
		align-items: flex-end;
		gap: 3px;
		height: 40px;
	}

	.fps__graph i {
		width: 5px;
		border-radius: 2px;
		background: var(--green);
		opacity: 0.7;
	}

	.fps__tag {
		margin-left: auto;
		font-size: 9px;
		color: var(--on-dark-3);
	}

	.widget__music {
		display: flex;
		align-items: center;
		gap: 0.8rem;
	}

	.music__meta {
		display: flex;
		flex-direction: column;
		line-height: 1.2;
		min-width: 0;
	}

	.music__meta strong {
		font-size: 0.9rem;
		color: var(--on-dark);
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}

	.music__meta span {
		font-size: var(--fs-micro);
		color: var(--on-dark-3);
	}

	.music__controls {
		display: flex;
		align-items: center;
		gap: 0.4rem;
		margin-left: auto;
	}

	.music__controls button {
		display: grid;
		place-items: center;
		width: 30px;
		height: 30px;
		border-radius: 50%;
		color: var(--on-dark-2);
		transition: color var(--dur-fast) var(--ease-out), background-color var(--dur-fast) var(--ease-out);
	}

	.music__controls button:hover {
		color: var(--on-dark);
		background: rgb(255 255 255 / 0.06);
	}

	.music__controls .music__play {
		background: var(--accent);
		color: var(--on-accent);
	}

	.music__controls .music__play:hover {
		background: var(--tomato-deep);
		color: var(--on-accent);
	}

	/* ---- Pager ------------------------------------------------------------- */
	.notch__pager {
		display: flex;
		align-items: center;
		gap: 0.75rem;
	}

	.pager__arrow {
		display: grid;
		place-items: center;
		width: 28px;
		height: 28px;
		border-radius: 8px;
		border: 1px solid var(--line-strong);
		color: var(--text-2);
		transition: color var(--dur-fast) var(--ease-out), background-color var(--dur-fast) var(--ease-out);
	}

	.pager__arrow:hover {
		color: var(--text);
		background: rgb(15 23 42 / 0.05);
	}

	.pager__dots {
		display: flex;
		gap: 0.4rem;
	}

	.pager__dot {
		width: 6px;
		height: 6px;
		border-radius: 50%;
		background: rgb(15 23 42 / 0.16);
		transition: background-color var(--dur-fast) var(--ease-out), transform var(--dur-fast) var(--ease-out);
	}

	.pager__dot:hover {
		background: rgb(15 23 42 / 0.32);
	}

	.pager__dot.active {
		background: var(--accent);
		transform: scale(1.25);
	}

	/* Points up instead of down, for the "previous" arrow. */
	:global(.flip) {
		transform: rotate(180deg);
	}
</style>
