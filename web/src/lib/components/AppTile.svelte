<script lang="ts">
	import Glyph from './Glyph.svelte';
	import type { Tile } from '$data/site';

	interface Props {
		tile: Tile;
		size?: number;
		/** Applied to the tile's corner radius, as a fraction of its size. */
		radius?: number;
		class?: string;
		/**
		 * Set when the tile sits next to a visible app name. The name would otherwise
		 * be announced twice, so the tile becomes purely presentational.
		 */
		decorative?: boolean;
	}

	let {
		tile,
		size = 38,
		radius = 0.29,
		class: className = '',
		decorative = false
	}: Props = $props();

	let radiusPx = $derived(Math.round(size * radius));
</script>

<span
	class="tile {className}"
	style:width="{size}px"
	style:height="{size}px"
	style:border-radius="{radiusPx}px"
	style:--tile-color={tile.from}
	style:color="#fff"
	title={decorative ? undefined : tile.name}
	aria-hidden={decorative ? 'true' : undefined}
>
	<Glyph name={tile.glyph} size={Math.round(size * 0.56)} />
	{#if !decorative}
		<span class="sr-only">{tile.name}</span>
	{/if}
</span>

<style>
	/* Flat fill, no gradient: the depth comes from two inset hairlines rather
	   than a tinted gradient wash. */
	.tile {
		position: relative;
		display: inline-grid;
		place-items: center;
		flex: none;
		background: var(--tile-color);
		box-shadow:
			inset 0 1px 0 rgb(255 255 255 / 0.26),
			inset 0 -2px 0 rgb(0 0 0 / 0.2),
			0 6px 16px -8px rgb(0 0 0 / 0.7);
	}
</style>
