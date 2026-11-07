<script lang="ts">
	import { onMount } from 'svelte';

	/**
	 * Static tiling noise behind the page, generated on the client so no
	 * asset ships. Low opacity, no animation.
	 */

	interface Props {
		/** Peak opacity of the grain layer. Keep this low; it is a texture. */
		opacity?: number;
	}

	let { opacity = 0.06 }: Props = $props();

	let node: HTMLDivElement | undefined = $state();

	onMount(() => {
		if (!node) return;

		const size = 160;
		const canvas = document.createElement('canvas');
		canvas.width = size;
		canvas.height = size;

		const ctx = canvas.getContext('2d');
		if (!ctx) return;

		const image = ctx.createImageData(size, size);
		const { data } = image;

		// Full-range, single-pixel grey noise: it both lightens and darkens the
		// near-black base by the same amount, and the fine grain stays in the
		// background rather than competing with the copy.
		for (let i = 0; i < data.length; i += 4) {
			const value = Math.random() * 255;
			data[i] = value;
			data[i + 1] = value;
			data[i + 2] = value;
			data[i + 3] = 255;
		}

		ctx.putImageData(image, 0, 0);
		node.style.backgroundImage = `url(${canvas.toDataURL('image/png')})`;
	});
</script>

<div class="grain" bind:this={node} style:opacity aria-hidden="true"></div>

<style>
	.grain {
		position: fixed;
		/* Oversized so no edge can ever be exposed. */
		inset: -8%;
		z-index: -1;
		pointer-events: none;
		background-repeat: repeat;
		background-size: 160px 160px;
	}
</style>
