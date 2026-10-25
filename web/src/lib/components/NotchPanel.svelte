<script lang="ts">
	import Glyph from './Glyph.svelte';
	import Icon from './Icon.svelte';
	import { dockTiles, type TourStepId } from '$data/site';

	/**
	 * One state of the notch, drawn to the app's own geometry.
	 *
	 * This is the single mock every surface reuses: the hero's desktop, the
	 * laptop screen in the scroll tour, and the compact step list that stands in
	 * for the tour when motion is off. It is the reason the page can explain
	 * every widget in one place and still never show a shape the app does not
	 * draw.
	 *
	 * Everything is sized in `cqw`, against the stage it is dropped into, so the
	 * same markup reads correctly on a 620px laptop screen and a 300px phone.
	 * The panel is decorative — the caption beside it carries the meaning — so
	 * it is hidden from assistive tech.
	 */

	interface Props {
		state?: TourStepId;
		/** Notch profile, matching the three Settings options. */
		shape?: 'amoled' | 'pill' | 'handle';
		class?: string;
	}

	let { state = 'quickview', shape = 'pill', class: className = '' }: Props = $props();

	const RING_CIRCUMFERENCE = 2 * Math.PI * 16;

	/** Dash length for a 0-100 level on the app's 288° arc. */
	function ringDash(percent: number): string {
		const swept = (RING_CIRCUMFERENCE * 0.8 * percent) / 100;
		return `${swept.toFixed(1)} ${RING_CIRCUMFERENCE.toFixed(1)}`;
	}

	const expanded = $derived(state !== 'quickview' && state !== 'gaming');

	const gauges = [
		{ icon: 'cpu' as const, name: 'CPU', value: '34%', percent: 34 },
		{ icon: 'gpu' as const, name: 'GPU', value: '12%', percent: 12 },
		{ icon: 'ram' as const, name: 'Memory', value: '61%', percent: 61 },
		{ icon: 'storage' as const, name: 'Storage', value: '48%', percent: 48 },
		{ icon: 'download' as const, name: 'Download', value: '1.2M/s', percent: 26 },
		{ icon: 'upload' as const, name: 'Upload', value: '8K/s', percent: 6 }
	];

	const forecast = [
		{ day: 'Mon', temp: '21°' },
		{ day: 'Tue', temp: '23°' },
		{ day: 'Wed', temp: '19°' },
		{ day: 'Thu', temp: '17°' },
		{ day: 'Fri', temp: '20°' },
		{ day: 'Sat', temp: '22°' },
		{ day: 'Sun', temp: '18°' }
	];

	const agenda = [
		{ time: '09:30', what: 'Standup' },
		{ time: '14:00', what: 'Design review' },
		{ time: '17:30', what: 'Ship 1.0.2' }
	];

	const hud = [
		{ label: 'FPS', value: '144' },
		{ label: 'CPU', value: '38%' },
		{ label: 'GPU', value: '91%' },
		{ label: 'PING', value: '12 ms' },
		{ label: 'NET', value: '1.4M/s' }
	];

	const tiles = dockTiles.slice(0, 7);
</script>

<div class="notch-stage {className}" aria-hidden="true">
	<div
		class="notch"
		class:notch--expanded={expanded}
		class:notch--game={state === 'gaming'}
		class:notch--handle={shape === 'handle'}
		data-state={state}
		data-shape={shape}
	>
		{#if state === 'quickview'}
			<div class="pill">
				<span class="pill__logo"><Icon name="logo" size="100%" /></span>
				<span class="pill__time">2:52</span>
				<span class="pill__date">25 Oct</span>
				<span class="pill__weather"><Icon name="sun" size="100%" /><b>23°</b></span>
				<span class="ring ring--battery">
					<svg viewBox="0 0 40 40">
						<g transform="rotate(126 20 20)">
							<circle class="ring__track" cx="20" cy="20" r="16" />
							<circle class="ring__bar" cx="20" cy="20" r="16" style:stroke-dasharray={ringDash(76)} />
						</g>
					</svg>
				</span>
				<span class="pill__bell">
					<Icon name="bell" size="100%" />
					<b>3</b>
				</span>
			</div>
		{:else if state === 'gaming'}
			<div class="hud">
				{#each hud as cell, index (cell.label)}
					{#if index > 0}<span class="hud__divider"></span>{/if}
					<span class="hud__cell">
						<strong>{cell.value}</strong>
						<span>{cell.label}</span>
					</span>
				{/each}
			</div>
		{:else if state === 'apps'}
			<div class="dock">
				{#each tiles as tile, index (tile.id)}
					<span
						class="dock__tile"
						class:dock__tile--lift={index === 3 || index === 4}
						style:--tile={tile.from}
					>
						<Glyph name={tile.glyph} />
					</span>
				{/each}
			</div>
		{:else if state === 'system'}
			<div class="page page--system">
				{#each gauges as gauge (gauge.name)}
					<span class="gauge">
						<span class="ring ring--gauge">
							<svg viewBox="0 0 40 40">
								<g transform="rotate(126 20 20)">
									<circle class="ring__track" cx="20" cy="20" r="16" />
									<circle class="ring__bar" cx="20" cy="20" r="16" style:stroke-dasharray={ringDash(gauge.percent)} />
								</g>
							</svg>
							<span class="gauge__glyph"><Icon name={gauge.icon} size="100%" /></span>
						</span>
						<span class="gauge__value">{gauge.value}</span>
						<span class="gauge__name">{gauge.name}</span>
					</span>
				{/each}
			</div>
		{:else if state === 'weather'}
			<div class="page page--weather">
				<div class="weather__now">
					<span class="weather__sun"><Icon name="sun" size="100%" /></span>
					<span class="weather__main">
						<strong>23°</strong>
						<span>Partly cloudy</span>
					</span>
					<span class="weather__place">Berlin</span>
				</div>
				<ul class="weather__days">
					{#each forecast as day, index (day.day)}
						<li class:today={index === 1}>
							<span>{day.day}</span>
							<b>{day.temp}</b>
						</li>
					{/each}
				</ul>
			</div>
		{:else if state === 'calendar'}
			<div class="page page--calendar">
				<span class="cal__day">
					<span class="cal__dow">Fri</span>
					<strong>23</strong>
				</span>
				<ul class="cal__list">
					{#each agenda as item (item.time)}
						<li>
							<span class="cal__time">{item.time}</span>
							<span class="cal__what">{item.what}</span>
						</li>
					{/each}
				</ul>
			</div>
		{:else if state === 'music'}
			<div class="page page--music">
				<span class="vinyl"><span class="vinyl__label"></span></span>
				<span class="music__meta">
					<strong>Midnight City</strong>
					<span>M83 · Hurry Up, We&rsquo;re Dreaming</span>
					<span class="track"><i></i></span>
					<span class="music__controls">
						<span class="transport"><Icon name="next" size="100%" class="flip" /></span>
						<span class="transport transport--play"><Icon name="play" size="100%" /></span>
						<span class="transport"><Icon name="next" size="100%" /></span>
					</span>
				</span>
				<span class="music__rings">
					<span class="ring"><svg viewBox="0 0 40 40"><g transform="rotate(126 20 20)"><circle class="ring__track" cx="20" cy="20" r="16" /><circle class="ring__bar" cx="20" cy="20" r="16" style:stroke-dasharray={ringDash(42)} /></g></svg><span class="gauge__glyph"><Icon name="volume" size="100%" /></span></span>
					<span class="ring"><svg viewBox="0 0 40 40"><g transform="rotate(126 20 20)"><circle class="ring__track" cx="20" cy="20" r="16" /><circle class="ring__bar" cx="20" cy="20" r="16" style:stroke-dasharray={ringDash(64)} /></g></svg><span class="gauge__glyph"><Icon name="battery" size="100%" /></span></span>
				</span>
			</div>
		{/if}
	</div>
</div>

<style>
	/* The stage is the size container: one panel unit is 1% of its width, so the
	   same mock scales from a phone to a laptop without a second layout. */
	.notch-stage {
		container-type: inline-size;
		display: flex;
		justify-content: center;
		width: 100%;
	}

	.notch {
		position: relative;
		display: flex;
		align-items: center;
		justify-content: center;
		width: 46cqw;
		min-height: 10.2cqw;
		padding: 1.3cqw 2.2cqw;
		border-radius: 0 0 2.6cqw 2.6cqw;
		border: 1px solid var(--on-dark-line-strong);
		background: rgb(12 12 12 / 0.94);
		box-shadow:
			inset 0 1px 0 rgb(255 255 255 / 0.07),
			0 2.4cqw 4cqw -2cqw rgb(0 0 0 / 0.85);
		color: var(--on-dark);
		overflow: hidden;
		transition:
			width 560ms var(--ease-out),
			min-height 560ms var(--ease-out),
			padding 560ms var(--ease-out),
			border-radius 560ms var(--ease-out),
			background-color 320ms var(--ease-out);
	}

	/* AMOLED: no rim, no sheen — the pill reads as a cut-out in the display. */
	.notch[data-shape='amoled'] {
		border-color: transparent;
		background: #000;
		box-shadow: inset 0 0 0 1px rgb(255 255 255 / 0.05);
	}

	.notch--expanded {
		width: 56cqw;
		min-height: 27cqw;
		padding: 2.4cqw;
		align-items: stretch;
	}

	.notch[data-state='apps'] {
		min-height: 16.5cqw;
	}

	.notch[data-state='music'] {
		min-height: 25cqw;
	}

	/* Transparent in games: body gone, thin rim, numbers carry their own shadow. */
	.notch--game {
		width: 54cqw;
		border-color: rgb(0 0 0 / 0.55);
		background: rgb(8 10 14 / 0.2);
		box-shadow: 0 2.4cqw 4.4cqw rgb(0 0 0 / 0.5);
	}

	/* Drag handle: slim, quiet, hovering just off the edge. */
	.notch--handle {
		width: 26cqw;
		min-height: 5.6cqw;
		margin-top: 1.2cqw;
		padding: 0;
		border-radius: 999px;
		border-color: rgb(255 255 255 / 0.16);
		background: rgb(255 255 255 / 0.14);
		box-shadow: none;
	}

	.notch--handle .pill {
		justify-content: center;
	}

	.notch--handle .pill > *:not(.pill__logo) {
		display: none;
	}

	/* ---- Quickview ------------------------------------------------------- */
	.pill {
		display: flex;
		align-items: center;
		gap: 1.1cqw;
		width: 100%;
		white-space: nowrap;
	}

	.pill__logo {
		display: grid;
		place-items: center;
		flex: none;
		width: 3cqw;
		color: var(--tomato);
	}

	.pill__time {
		font-family: var(--font-display);
		font-size: 2.7cqw;
		font-weight: 700;
		letter-spacing: -0.02em;
		line-height: 1;
		color: var(--on-dark);
		font-variant-numeric: tabular-nums;
	}

	.pill__date {
		font-family: var(--font-mono);
		font-size: 1.45cqw;
		letter-spacing: 0.05em;
		text-transform: uppercase;
		color: var(--on-dark-3);
	}

	.pill__weather {
		display: inline-flex;
		align-items: center;
		gap: 0.65cqw;
		margin-left: auto;
		font-size: 1.75cqw;
		color: #fbbf24;
	}

	.pill__weather :global(svg) {
		width: 2.2cqw;
		height: 2.2cqw;
	}

	.pill__weather b {
		font-family: var(--font-mono);
		font-size: 1.7cqw;
		font-weight: 500;
		letter-spacing: 0.02em;
		color: var(--on-dark-2);
	}

	.pill__bell {
		position: relative;
		display: grid;
		place-items: center;
		width: 2.4cqw;
		color: var(--on-dark-2);
	}

	.pill__bell :global(svg) {
		width: 2.4cqw;
		height: 2.4cqw;
	}

	.pill__bell b {
		position: absolute;
		top: -0.7cqw;
		right: -0.6cqw;
		display: grid;
		place-items: center;
		min-width: 1.7cqw;
		height: 1.7cqw;
		padding-inline: 0.3cqw;
		border-radius: 999px;
		background: var(--tomato);
		color: #fff;
		font-family: var(--font-mono);
		font-size: 1.05cqw;
		font-weight: 700;
	}

	/* ---- Rings ----------------------------------------------------------- */
	.ring {
		position: relative;
		display: grid;
		place-items: center;
		flex: none;
		width: 3.6cqw;
		height: 3.6cqw;
	}

	.ring svg {
		position: absolute;
		inset: 0;
		width: 100%;
		height: 100%;
	}

	.ring__track,
	.ring__bar {
		fill: none;
		stroke-width: 4;
		stroke-linecap: round;
	}

	.ring__track {
		stroke: rgb(245 242 234 / 0.14);
	}

	.ring__bar {
		stroke: var(--tomato);
	}

	.ring--battery .ring__bar {
		stroke: var(--on-dark-2);
	}

	/* ---- Gaming HUD ------------------------------------------------------ */
	.hud {
		display: flex;
		align-items: center;
		justify-content: space-between;
		width: 100%;
	}

	.hud__cell {
		display: flex;
		flex: 1 1 0;
		flex-direction: column;
		align-items: center;
		min-width: 0;
		line-height: 1.1;
	}

	.hud__cell strong {
		font-family: var(--font-mono);
		font-size: 2.3cqw;
		font-weight: 600;
		letter-spacing: 0;
		color: #fff;
		text-shadow: 0 1px 4px rgb(0 0 0 / 0.9);
		font-variant-numeric: tabular-nums;
	}

	.hud__cell span {
		font-family: var(--font-mono);
		font-size: 1.15cqw;
		letter-spacing: 0.1em;
		color: rgb(245 242 234 / 0.55);
		text-shadow: 0 1px 4px rgb(0 0 0 / 0.9);
	}

	.hud__divider {
		flex: none;
		width: 1px;
		height: 4.2cqw;
		background: rgb(0 0 0 / 0.5);
	}

	/* ---- Dock ------------------------------------------------------------ */
	.dock {
		display: flex;
		align-items: flex-end;
		justify-content: center;
		gap: 1.1cqw;
		width: 100%;
	}

	.dock__tile {
		position: relative;
		display: grid;
		place-items: center;
		flex: none;
		width: 5.8cqw;
		height: 5.8cqw;
		border-radius: 1.8cqw;
		background: var(--tile);
		box-shadow:
			inset 0 1px 0 rgb(255 255 255 / 0.26),
			inset 0 -2px 0 rgb(0 0 0 / 0.2),
			0 1cqw 2cqw -1cqw rgb(0 0 0 / 0.7);
	}

	.dock__tile :global(svg) {
		width: 56%;
		height: 56%;
		color: #fff;
	}

	/* A frozen fisheye beat: the two middle tiles sit magnified, exactly where
	   the cursor would be resting. */
	.dock__tile--lift {
		transform: translateY(-0.5cqw) scale(1.14);
	}

	/* ---- Shared page chrome --------------------------------------------- */
	.page {
		display: flex;
		width: 100%;
		min-width: 0;
		color: var(--on-dark);
	}

	/* ---- System page ----------------------------------------------------- */
	.page--system {
		align-items: center;
		justify-content: space-between;
		gap: 0.8cqw;
	}

	.gauge {
		display: flex;
		flex: 1 1 0;
		flex-direction: column;
		align-items: center;
		gap: 0.55cqw;
		min-width: 0;
	}

	.ring--gauge {
		width: 8.4cqw;
		height: 8.4cqw;
	}

	.gauge__glyph {
		display: grid;
		place-items: center;
		width: 2.6cqw;
		height: 2.6cqw;
		color: var(--on-dark);
	}

	.gauge__glyph :global(svg) {
		width: 100%;
		height: 100%;
	}

	.gauge__value {
		font-family: var(--font-mono);
		font-size: 1.8cqw;
		line-height: 1;
		color: var(--on-dark);
		font-variant-numeric: tabular-nums;
	}

	.gauge__name {
		font-family: var(--font-mono);
		font-size: 1.1cqw;
		letter-spacing: 0.12em;
		text-transform: uppercase;
		color: var(--on-dark-3);
	}

	/* ---- Weather page ---------------------------------------------------- */
	.page--weather {
		flex-direction: column;
		justify-content: space-between;
		gap: 1.6cqw;
	}

	.weather__now {
		display: flex;
		align-items: center;
		gap: 1.4cqw;
	}

	.weather__sun {
		display: grid;
		place-items: center;
		flex: none;
		width: 8.6cqw;
		height: 8.6cqw;
		border-radius: 2.4cqw;
		background: rgb(251 191 36 / 0.14);
		color: #fbbf24;
	}

	.weather__sun :global(svg) {
		width: 5.4cqw;
		height: 5.4cqw;
	}

	.weather__main {
		display: flex;
		flex-direction: column;
		line-height: 1.02;
	}

	.weather__main strong {
		font-family: var(--font-display);
		font-size: 5.6cqw;
		letter-spacing: -0.04em;
		color: var(--on-dark);
	}

	.weather__main span {
		font-size: 1.5cqw;
		color: var(--on-dark-3);
	}

	.weather__place {
		margin-left: auto;
		font-family: var(--font-mono);
		font-size: 1.2cqw;
		letter-spacing: 0.12em;
		text-transform: uppercase;
		color: var(--on-dark-3);
	}

	.weather__days {
		display: flex;
		justify-content: space-between;
		gap: 0.4cqw;
		padding-top: 1.2cqw;
		border-top: 1px solid var(--on-dark-line);
	}

	.weather__days li {
		display: flex;
		flex: 1 1 0;
		flex-direction: column;
		align-items: center;
		gap: 0.45cqw;
		font-family: var(--font-mono);
		font-size: 1.15cqw;
		letter-spacing: 0.08em;
		text-transform: uppercase;
		color: var(--on-dark-3);
	}

	.weather__days li b {
		font-size: 1.7cqw;
		font-weight: 500;
		letter-spacing: 0;
		color: var(--on-dark-2);
	}

	.weather__days li.today b {
		color: var(--tomato);
	}

	/* ---- Calendar page --------------------------------------------------- */
	.page--calendar {
		align-items: center;
		gap: 2cqw;
	}

	.cal__day {
		display: flex;
		flex: none;
		flex-direction: column;
		align-items: center;
		padding-right: 2cqw;
		border-right: 1px solid var(--on-dark-line);
	}

	.cal__dow {
		font-family: var(--font-mono);
		font-size: 1.2cqw;
		letter-spacing: 0.14em;
		text-transform: uppercase;
		color: var(--tomato);
	}

	.cal__day strong {
		font-family: var(--font-display);
		font-size: 5.4cqw;
		line-height: 1;
		letter-spacing: -0.04em;
		color: var(--on-dark);
	}

	.cal__list {
		display: flex;
		flex: 1;
		flex-direction: column;
		gap: 1.05cqw;
		min-width: 0;
	}

	.cal__list li {
		display: flex;
		align-items: baseline;
		gap: 1.1cqw;
		font-size: 1.65cqw;
	}

	.cal__time {
		font-family: var(--font-mono);
		font-size: 1.2cqw;
		color: var(--on-dark-3);
	}

	.cal__what {
		color: var(--on-dark);
	}

	/* ---- Music page ------------------------------------------------------ */
	.page--music {
		align-items: center;
		gap: 2.2cqw;
	}

	.vinyl {
		position: relative;
		display: grid;
		place-items: center;
		flex: none;
		width: 11cqw;
		height: 11cqw;
		border-radius: 50%;
		background: #0a0a0a;
		box-shadow:
			inset 0 0 0 0.35cqw rgb(255 255 255 / 0.06),
			inset 0 0 0 1.6cqw rgb(255 255 255 / 0.04),
			0 0 0 1px rgb(255 255 255 / 0.08);
	}

	.vinyl__label {
		width: 4.2cqw;
		height: 4.2cqw;
		border-radius: 50%;
		background: var(--tomato);
		box-shadow: inset 0 0 0 1.1cqw rgb(0 0 0 / 0.18);
	}

	.music__meta {
		display: flex;
		flex: 1;
		flex-direction: column;
		gap: 0.7cqw;
		min-width: 0;
	}

	.music__meta strong {
		font-family: var(--font-display);
		font-size: 2.5cqw;
		letter-spacing: -0.02em;
		line-height: 1.05;
		color: var(--on-dark);
	}

	.music__meta > span {
		font-size: 1.35cqw;
		color: var(--on-dark-3);
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}

	.track {
		position: relative;
		height: 0.6cqw;
		margin-top: 0.5cqw;
		border-radius: 999px;
		background: rgb(245 242 234 / 0.14);
	}

	.track i {
		position: absolute;
		inset: 0 58% 0 0;
		border-radius: 999px;
		background: var(--on-dark);
	}

	.music__controls {
		display: flex;
		align-items: center;
		gap: 1.1cqw;
		margin-top: 0.4cqw;
	}

	.transport {
		display: grid;
		place-items: center;
		width: 3.2cqw;
		height: 3.2cqw;
		color: var(--on-dark-2);
	}

	.transport :global(svg) {
		width: 2.3cqw;
		height: 2.3cqw;
	}

	.transport :global(.flip) {
		transform: rotate(180deg);
	}

	.transport--play {
		width: 4.4cqw;
		height: 4.4cqw;
		border-radius: 50%;
		background: var(--on-dark);
		color: #0b0b0b;
	}

	.transport--play :global(svg) {
		width: 2.1cqw;
		height: 2.1cqw;
	}

	.music__rings {
		display: flex;
		flex: none;
		flex-direction: column;
		gap: 0.9cqw;
	}

	.music__rings .ring {
		width: 5.2cqw;
		height: 5.2cqw;
	}
</style>
