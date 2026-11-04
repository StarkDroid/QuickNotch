<script lang="ts">
	import Glyph from './Glyph.svelte';
	import Icon from './Icon.svelte';
	import { dockTiles, notchDemo, type TourStepId } from '$data/site';

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

	const gauges = notchDemo.gauges;
	const forecast = notchDemo.forecast;
	const dates = notchDemo.dates;
	const hud = notchDemo.hud;

	const tiles = dockTiles.slice(0, 6);
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
				<span class="pill__time">{notchDemo.time}</span>
				<span class="pill__date">{notchDemo.date}</span>
				<span class="pill__weather"><Icon name="sun" size="100%" /><b>{notchDemo.temp}</b></span>
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
					<b>{notchDemo.notifications}</b>
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
				<div class="wx__now">
					<span class="wx__top">
						<span class="wx__glyph"><Icon name="sun" size="100%" /></span>
						<strong>{notchDemo.weather.now}</strong>
					</span>
					<span class="wx__place">{notchDemo.weather.place}</span>
					<span class="wx__hilow">{notchDemo.weather.highLow}</span>
					<span class="wx__extra">{notchDemo.weather.detail}</span>
				</div>
				<span class="page__divider"></span>
				{#each forecast as day (day.dow)}
					<span class="wx__day">
						<span class="wx__dow" class:today={day.today}>{day.dow}</span>
						<span class="wx__icon"><Icon name={day.icon} size="100%" /></span>
						<span class="wx__hi">{day.high}</span>
						<span class="wx__lo">{day.low}</span>
					</span>
				{/each}
			</div>
		{:else if state === 'calendar'}
			<div class="page page--calendar">
				<span class="cal__today">
					<strong>{notchDemo.calendar.day}</strong>
					<span class="cal__meta">
						<b>{notchDemo.calendar.weekday}</b>
						<span>{notchDemo.calendar.month}</span>
					</span>
				</span>
				<span class="page__divider"></span>
				{#each dates as date, index (date.label)}
					{#if index > 0}<span class="page__divider page__divider--sm"></span>{/if}
					<span class="cal__tile">
						<b>{date.label}</b>
						<span class="cal__count">{date.count}</span>
					</span>
				{/each}
			</div>
		{:else if state === 'music'}
			<div class="page page--music">
				<span class="vinyl"><span class="vinyl__label"></span></span>
				<span class="music__meta">
					<strong>{notchDemo.music.title}</strong>
					<span>{notchDemo.music.artist}</span>
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
		width: 34cqw;
		min-height: 9cqw;
		padding: 1.1cqw 1.8cqw;
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
		width: 40cqw;
		min-height: 16cqw;
		padding: 1.5cqw 1.6cqw;
		align-items: stretch;
	}

	.notch[data-state='apps'] {
		width: 38cqw;
		min-height: 12cqw;
	}

	.notch[data-state='music'] {
		min-height: 18cqw;
	}

	/* Transparent in games: body gone, thin rim, numbers carry their own shadow. */
	.notch--game {
		width: 42cqw;
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
		width: 2.6cqw;
		color: var(--tomato);
	}

	.pill__time {
		font-family: var(--font-display);
		font-size: 2.4cqw;
		font-weight: 700;
		letter-spacing: -0.02em;
		line-height: 1;
		color: var(--on-dark);
		font-variant-numeric: tabular-nums;
	}

	.pill__date {
		font-family: var(--font-mono);
		font-size: 1.25cqw;
		letter-spacing: 0.04em;
		text-transform: uppercase;
		color: var(--on-dark-3);
	}

	.pill__weather {
		display: inline-flex;
		align-items: center;
		gap: 0.65cqw;
		margin-left: auto;
		font-size: 1.55cqw;
		color: #fbbf24;
	}

	.pill__weather :global(svg) {
		width: 1.9cqw;
		height: 1.9cqw;
	}

	.pill__weather b {
		font-family: var(--font-mono);
		font-size: 1.45cqw;
		font-weight: 500;
		letter-spacing: 0.02em;
		color: var(--on-dark-2);
	}

	.pill__bell {
		position: relative;
		display: grid;
		place-items: center;
		width: 2.1cqw;
		color: var(--on-dark-2);
	}

	.pill__bell :global(svg) {
		width: 2.1cqw;
		height: 2.1cqw;
	}

	.pill__bell b {
		position: absolute;
		top: -0.7cqw;
		right: -0.6cqw;
		display: grid;
		place-items: center;
		min-width: 1.5cqw;
		height: 1.5cqw;
		padding-inline: 0.3cqw;
		border-radius: 999px;
		background: var(--tomato);
		color: #fff;
		font-family: var(--font-mono);
		font-size: 0.95cqw;
		font-weight: 700;
	}

	/* ---- Rings ----------------------------------------------------------- */
	.ring {
		position: relative;
		display: grid;
		place-items: center;
		flex: none;
		width: 3.2cqw;
		height: 3.2cqw;
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
		font-size: 2.1cqw;
		font-weight: 600;
		letter-spacing: 0;
		color: #fff;
		text-shadow: 0 1px 4px rgb(0 0 0 / 0.9);
		font-variant-numeric: tabular-nums;
	}

	.hud__cell span {
		font-family: var(--font-mono);
		font-size: 1.02cqw;
		letter-spacing: 0.1em;
		color: rgb(245 242 234 / 0.55);
		text-shadow: 0 1px 4px rgb(0 0 0 / 0.9);
	}

	.hud__divider {
		flex: none;
		width: 1px;
		height: 3.8cqw;
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
		width: 5.3cqw;
		height: 5.3cqw;
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
		gap: 0.6cqw;
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
		width: 5.4cqw;
		height: 5.4cqw;
	}

	.gauge__glyph {
		display: grid;
		place-items: center;
		width: 1.9cqw;
		height: 1.9cqw;
		color: var(--on-dark);
	}

	.gauge__glyph :global(svg) {
		width: 100%;
		height: 100%;
	}

	.gauge__value {
		font-family: var(--font-mono);
		font-size: 1.35cqw;
		line-height: 1;
		color: var(--on-dark);
		font-variant-numeric: tabular-nums;
	}

	.gauge__name {
		font-family: var(--font-mono);
		font-size: 0.85cqw;
		letter-spacing: 0.1em;
		text-transform: uppercase;
		color: var(--on-dark-3);
	}

	/* Hairline between the anchor block and the day tiles, like the app's
	   NotchBorder dividers. */
	.page__divider {
		flex: none;
		align-self: stretch;
		width: 1px;
		margin-inline: 1.2cqw;
		background: rgb(245 242 234 / 0.12);
	}

	.page__divider--sm {
		align-self: center;
		height: 7cqw;
		margin-inline: 0;
	}

	/* ---- Weather page ----------------------------------------------------
	   Mirrors WeatherNotchPage: current conditions anchored left (glyph +
	   temperature, place, high/low, precip · UV), then one tile per outlook day
	   — weekday over glyph over high over low. */
	.page--weather {
		align-items: center;
		gap: 0.5cqw;
	}

	.wx__now {
		display: flex;
		flex: none;
		flex-direction: column;
		gap: 0.3cqw;
		width: 12.5cqw;
	}

	.wx__top {
		display: flex;
		align-items: center;
		gap: 0.8cqw;
	}

	.wx__glyph {
		display: grid;
		place-items: center;
		flex: none;
		width: 3.4cqw;
		height: 3.4cqw;
		color: #fbbf24;
	}

	.wx__glyph :global(svg) {
		width: 100%;
		height: 100%;
	}

	.wx__top strong {
		font-family: var(--font-display);
		font-size: 2.6cqw;
		font-weight: 700;
		letter-spacing: -0.03em;
		line-height: 1;
		color: var(--on-dark);
		font-variant-numeric: tabular-nums;
	}

	.wx__place {
		font-size: 1.15cqw;
		color: var(--on-dark-2);
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}

	.wx__hilow {
		font-size: 1.05cqw;
		color: var(--on-dark-2);
		font-variant-numeric: tabular-nums;
	}

	.wx__extra {
		font-size: 1cqw;
		color: var(--on-dark-3);
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}

	.wx__day {
		display: flex;
		flex: 1 1 0;
		flex-direction: column;
		align-items: center;
		gap: 0.4cqw;
		min-width: 0;
	}

	.wx__dow {
		font-family: var(--font-mono);
		font-size: 0.95cqw;
		letter-spacing: 0.06em;
		text-transform: uppercase;
		color: var(--on-dark-3);
	}

	.wx__dow.today {
		color: var(--on-dark);
		font-weight: 700;
	}

	.wx__icon {
		display: grid;
		place-items: center;
		width: 1.9cqw;
		height: 1.9cqw;
		color: var(--on-dark-2);
	}

	.wx__icon :global(svg) {
		width: 100%;
		height: 100%;
	}

	.wx__hi {
		font-family: var(--font-mono);
		font-size: 1.2cqw;
		font-weight: 600;
		line-height: 1;
		color: var(--on-dark);
	}

	.wx__lo {
		font-family: var(--font-mono);
		font-size: 1.05cqw;
		line-height: 1;
		color: var(--on-dark-3);
	}

	/* ---- Calendar page ---------------------------------------------------
	   Mirrors CalendarNotchPage: today anchored left (big date in accent with
	   weekday + month beside it), then one tile per upcoming date — "24 Oct"
	   over its red count badge. */
	.page--calendar {
		align-items: center;
		gap: 0.6cqw;
	}

	.cal__today {
		display: flex;
		flex: none;
		align-items: center;
		gap: 0.9cqw;
		padding-inline: 0.6cqw;
	}

	.cal__today strong {
		font-family: var(--font-display);
		font-size: 4.4cqw;
		font-weight: 600;
		line-height: 1;
		letter-spacing: -0.03em;
		color: var(--tomato);
	}

	.cal__meta {
		display: flex;
		flex-direction: column;
		gap: 0.15cqw;
	}

	.cal__meta b {
		font-family: var(--font-mono);
		font-size: 1.15cqw;
		font-weight: 500;
		letter-spacing: 0.1em;
		text-transform: uppercase;
		color: var(--on-dark);
	}

	.cal__meta span {
		font-family: var(--font-mono);
		font-size: 1.05cqw;
		letter-spacing: 0.1em;
		text-transform: uppercase;
		color: var(--on-dark-3);
	}

	.cal__tile {
		display: flex;
		flex: 1 1 0;
		flex-direction: column;
		align-items: center;
		gap: 0.55cqw;
		min-width: 0;
	}

	.cal__tile b {
		font-size: 1.35cqw;
		font-weight: 600;
		letter-spacing: 0;
		color: var(--on-dark);
		white-space: nowrap;
	}

	/* The app's CountBadge: red pill, white count, same ramp everywhere. */
	.cal__count {
		display: grid;
		place-items: center;
		min-width: 1.9cqw;
		height: 1.5cqw;
		padding-inline: 0.45cqw;
		border-radius: 999px;
		background: var(--tomato);
		color: #fff;
		font-family: var(--font-mono);
		font-size: 0.95cqw;
		font-weight: 700;
		line-height: 1;
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
		width: 9.6cqw;
		height: 9.6cqw;
		border-radius: 50%;
		background: #0a0a0a;
		box-shadow:
			inset 0 0 0 0.35cqw rgb(255 255 255 / 0.06),
			inset 0 0 0 1.6cqw rgb(255 255 255 / 0.04),
			0 0 0 1px rgb(255 255 255 / 0.08);
	}

	.vinyl__label {
		width: 3.7cqw;
		height: 3.7cqw;
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
		font-size: 2.15cqw;
		letter-spacing: -0.02em;
		line-height: 1.05;
		color: var(--on-dark);
	}

	.music__meta > span {
		font-size: 1.15cqw;
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
		width: 2.8cqw;
		height: 2.8cqw;
		color: var(--on-dark-2);
	}

	.transport :global(svg) {
		width: 2cqw;
		height: 2cqw;
	}

	.transport :global(.flip) {
		transform: rotate(180deg);
	}

	.transport--play {
		width: 3.9cqw;
		height: 3.9cqw;
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
		width: 4.6cqw;
		height: 4.6cqw;
	}
</style>
