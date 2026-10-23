<script lang="ts">
	import AppTile from './AppTile.svelte';
	import Icon from './Icon.svelte';
	import { dockTiles, widgets } from '$data/site';
	import { prefersReducedMotion } from '$lib/motion/gsap';

	/**
	 * The glanceable notch, showing one widget at a time.
	 *
	 * "Vertical scroll pagination": scrolling over the notch (or using the left /
	 * right arrows below) moves through the widget stack — quick launch, system
	 * rings, weather, calendar and live activities. Each switch slides the next
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

	/**
	 * The System page, at demo scale: one ring per metric, the glyph inside the
	 * arc and the live value beneath — the same indicator the app draws, swept
	 * over 288° with the 20% gap held at the bottom.
	 */
	const systemMetrics: {
		icon: 'cpu' | 'gpu' | 'ram' | 'storage' | 'download' | 'upload';
		name: string;
		value: string;
		percent: number;
	}[] = [
		{ icon: 'cpu', name: 'CPU', value: '34%', percent: 34 },
		{ icon: 'gpu', name: 'GPU', value: '12%', percent: 12 },
		{ icon: 'ram', name: 'Memory', value: '61%', percent: 61 },
		{ icon: 'storage', name: 'Storage', value: '48%', percent: 48 },
		{ icon: 'download', name: 'Download', value: '1.2M/s', percent: 26 },
		{ icon: 'upload', name: 'Upload', value: '8K/s', percent: 6 }
	];

	/** The ring geometry, shared by every gauge: r=16, 288° of the 360°. */
	const RING_CIRCUMFERENCE = 2 * Math.PI * 16;

	/** Dash length for a level, as an `stroke-dasharray` value. */
	function ringDash(percent: number): string {
		const swept = (RING_CIRCUMFERENCE * 0.8 * percent) / 100;
		return `${swept.toFixed(1)} ${RING_CIRCUMFERENCE.toFixed(1)}`;
	}

	const agenda = [
		{ time: '09:30', what: 'Standup' },
		{ time: '14:00', what: 'Design review' },
		{ time: '17:30', what: 'Ship 1.0.2' }
	];

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
		aria-label="{label}, {current.label}"
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
							{#each systemMetrics as metric (metric.name)}
								<span class="gauge" role="img" aria-label="{metric.name}: {metric.value}" title="{metric.name}: {metric.value}">
									<span class="gauge__dial">
										<svg viewBox="0 0 40 40" aria-hidden="true">
											<g transform="rotate(126 20 20)">
												<circle class="dial__track" cx="20" cy="20" r="16" />
												<circle
													class="dial__bar"
													cx="20"
													cy="20"
													r="16"
													style:stroke-dasharray={ringDash(metric.percent)}
												/>
											</g>
										</svg>
										<Icon name={metric.icon} size={13} />
									</span>
									<span class="gauge__value mono">{metric.value}</span>
								</span>
							{/each}
						</div>
					{:else if current.id === 'calendar'}
						<div class="widget__calendar">
							<span class="cal__day">
								<span class="cal__dow mono">FRI</span>
								<strong>23</strong>
							</span>
							<ul class="cal__list">
								{#each agenda as item (item.time)}
									<li>
										<span class="cal__time mono">{item.time}</span>
										<span class="cal__what">{item.what}</span>
									</li>
								{/each}
							</ul>
							<span class="cal__tag mono">Google Calendar</span>
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

	/* System rings: six small gauges, glyph inside, value under — the notch
	   page's actual indicator, at demo scale. */
	.widget__system {
		display: flex;
		align-items: flex-start;
		justify-content: space-between;
		gap: 0.35rem;
		width: 100%;
	}

	.gauge {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 0.22rem;
	}

	.gauge__dial {
		position: relative;
		display: grid;
		place-items: center;
		width: 32px;
		height: 32px;
		color: var(--on-dark);
	}

	.gauge__dial svg {
		position: absolute;
		inset: 0;
		width: 100%;
		height: 100%;
	}

	.dial__track,
	.dial__bar {
		fill: none;
		stroke-width: 2.6;
		stroke-linecap: round;
	}

	.dial__track {
		stroke: rgb(255 255 255 / 0.16);
		stroke-dasharray: 80.4 100.5;
	}

	.dial__bar {
		stroke: currentColor;
	}

	.gauge__value {
		font-size: 9px;
		font-variant-numeric: tabular-nums;
		letter-spacing: 0.02em;
		color: var(--on-dark-2);
	}

	/* Calendar: the day stamp, the agenda, and where it came from. */
	.widget__calendar {
		display: flex;
		align-items: center;
		gap: 0.75rem;
		width: 100%;
	}

	.cal__day {
		display: flex;
		flex-direction: column;
		align-items: center;
		line-height: 1;
		padding: 0.3rem 0.55rem;
		border-radius: 12px;
		background: rgb(232 72 28 / 0.14);
		color: var(--on-accent);
		flex: none;
	}

	.cal__dow {
		font-size: 8px;
		letter-spacing: 0.12em;
		color: #f6a58c;
	}

	.cal__day strong {
		font-family: var(--font-display);
		font-size: 1.25rem;
		color: var(--on-dark);
	}

	.cal__list {
		display: flex;
		flex-direction: column;
		gap: 0.18rem;
		min-width: 0;
		flex: 1;
		list-style: none;
	}

	.cal__list li {
		display: flex;
		align-items: baseline;
		gap: 0.5rem;
		font-size: 11px;
		line-height: 1.25;
	}

	.cal__time {
		font-size: 9px;
		color: var(--on-dark-3);
		flex: none;
	}

	.cal__what {
		color: var(--on-dark);
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}

	.cal__tag {
		flex: none;
		font-size: 8px;
		letter-spacing: 0.08em;
		color: var(--on-dark-3);
		writing-mode: vertical-rl;
		text-orientation: mixed;
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
