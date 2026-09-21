<script lang="ts">
	interface Props {
		items: string[];
		/** Seconds for one full pass. */
		duration?: number;
		direction?: 'left' | 'right';
		gap?: string;
		class?: string;
	}

	let { items, duration = 34, direction = 'left', gap = '3.5rem', class: className = '' }: Props = $props();
</script>

<div
	class="marquee {className}"
	data-direction={direction}
	style:--marquee-duration="{duration}s"
	style:--marquee-gap={gap}
	aria-hidden="true"
>
	{#each [0, 1] as copy (copy)}
		<div class="marquee__track">
			{#each items as item, index (index)}
				<span class="marquee__item">{item}</span>
			{/each}
		</div>
	{/each}
</div>

<style>
	.marquee__item {
		display: inline-flex;
		align-items: center;
		gap: 3.5rem;
		font-family: var(--font-display);
		font-size: clamp(1.1rem, 2.6vw, 1.9rem);
		font-weight: 700;
		letter-spacing: -0.03em;
		white-space: nowrap;
		color: rgb(15 23 42 / 0.12);
	}
</style>
