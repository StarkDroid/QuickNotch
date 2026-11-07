<script lang="ts">
	import Icon from './Icon.svelte';
	import { calendarDemo } from '$data/site';

	/**
	 * The desktop calendar page mock; connected switches between the live
	 * view and the connect page's waiting state.
	 */

	interface Props {
		/** Paint the expanded island, or just the slim collapsed pill. */
		expanded?: boolean;
		/** Show live schedule content (false = muted waiting state). */
		connected?: boolean;
		/** Notch variant, matching the theme cards. */
		theme?: 'amoled' | 'pill' | 'handle';
	}

	let { expanded = true, connected = true, theme = 'pill' }: Props = $props();

	const highlightClass = $derived(`cn-hl cn-hl--${theme}`);
</script>

<div class="cn" class:cn--expanded={expanded}>
	<!-- The fat schedule page -->
	<div class="cn-island" class:cn--dim={!connected}>
		<div class="cn-today">
			<span class="cn-today__dow mono">{connected ? calendarDemo.weekday : calendarDemo.connecting}</span>
			<span class="cn-today__num">{calendarDemo.day}</span>
		</div>

		<div class="cn-days" aria-hidden="true">
			{#each calendarDemo.days as day, i (day)}
				<div class="cn-day" class:cn-day--now={i === 0 && connected}>
					<span class="cn-day__name">{day}</span>
					<span class="cn-day__count">{calendarDemo.times[i]}</span>
				</div>
			{/each}
		</div>

		<div class="cn-ticker" class:cn-ticker--on={connected}>
			<span class="cn-ticker__dot"></span>
			<span>{connected ? calendarDemo.event : calendarDemo.waiting}</span>
		</div>
	</div>

	<!-- The collapsed pill, badged -->
	<div class="cn-pill" class:cn--dim={!connected}>
		<span class="cn-pill__glyph"><span class={highlightClass}></span></span>
		<span class="cn-pill__count">{connected ? calendarDemo.count : calendarDemo.idle}</span>
	</div>
</div>

<style>
	.cn {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 0.9rem;
	}

	/* Shared on-dark capsule recipe. */
	.cn-island,
	.cn-pill {
		position: relative;
		background: #0e0c09;
		border: 1px solid var(--on-dark-line);
		color: var(--on-dark);
		box-shadow: 6px 6px 0 rgb(33 28 19 / 0.92);
	}

	.cn-island {
		display: flex;
		align-items: stretch;
		gap: 1.1rem;
		width: min(26rem, 100%);
		padding: 1.05rem 1.25rem;
		border-radius: 999px;
	}

	.cn-today {
		display: flex;
		flex-direction: column;
		gap: 0.12rem;
		min-width: 3.4rem;
		padding-right: 1.1rem;
		border-right: 1px solid var(--on-dark-line);
	}

	.cn-today__dow {
		color: var(--on-dark-3);
	}

	.cn-today__num {
		font-family: var(--font-display);
		font-weight: 700;
		font-size: 2.4rem;
		line-height: 1;
		letter-spacing: -0.03em;
		color: var(--accent);
	}

	.cn-days {
		display: flex;
		align-items: center;
		gap: 0.7rem;
	}

	.cn-day {
		display: flex;
		flex-direction: column;
		gap: 0.25rem;
		padding: 0.45rem 0.75rem;
		border-radius: var(--r-sm);
		border: 1px solid transparent;
	}

	.cn-day--now {
		border-color: var(--accent-line);
		background: rgb(232 72 28 / 0.12);
	}

	.cn-day__name {
		font-family: var(--font-mono);
		font-size: var(--fs-micro);
		letter-spacing: 0.1em;
		text-transform: uppercase;
		color: var(--on-dark-2);
	}

	.cn-day__count {
		font-family: var(--font-display);
		font-size: 0.92rem;
		font-weight: 600;
		color: var(--on-dark);
	}

	.cn-ticker {
		align-self: center;
		display: flex;
		align-items: center;
		gap: 0.5rem;
		margin-left: auto;
		font-size: var(--fs-micro);
		color: var(--on-dark-3);
		white-space: nowrap;
	}

	.cn-ticker__dot {
		width: 0.45rem;
		height: 0.45rem;
		border-radius: 50%;
		background: transparent;
	}

	.cn-ticker--on .cn-ticker__dot {
		background: var(--accent);
		box-shadow: 0 0 0 3px rgb(232 72 28 / 0.25);
	}

	/* The slim collapsed pill with the day's count badge. */
	.cn-pill {
		display: flex;
		align-items: center;
		gap: 0.55rem;
		width: 8.6rem;
		height: 1.8rem;
		padding: 0 0.75rem;
		border-radius: 999px;
	}

	.cn-pill__glyph {
		display: grid;
		place-items: center;
	}

	.cn-pill__glyph :global(svg) {
		width: 0.85rem;
		height: 0.85rem;
	}

	/* Accent sticker sitting on the calendar glyph. */
	.cn-hl {
		display: inline-block;
		width: 0.85rem;
		height: 0.85rem;
		border-radius: 0.3rem;
		background: var(--accent);
	}

	.cn-hl--amoled {
		transform: rotate(-4deg);
	}

	.cn-hl--handle {
		width: 0.9rem;
		height: 0.3rem;
		border-radius: 999px;
		transform: rotate(-2deg);
	}

	.cn-pill__count {
		font-family: var(--font-display);
		font-weight: 700;
		font-size: 0.85rem;
		color: var(--accent);
	}

	/* Waiting state: drop to faint outlines so the empty-of-data reads as
	   "not connected yet" rather than a broken widget. */
	.cn--dim {
		filter: saturate(0.25);
		opacity: 0.55;
	}

	.cn--dim .cn-ticker__dot {
		background: transparent;
	}
</style>
