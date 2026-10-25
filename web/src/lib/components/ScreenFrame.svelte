<script lang="ts">
	import type { Snippet } from 'svelte';
	import Icon from './Icon.svelte';

	/**
	 * A stylised desktop "screen" used throughout the site to place the notch in
	 * context. The notch renders *above* the menu bar, exactly like the real
	 * overlay window does when it hugs the top edge.
	 */

	interface Props {
		/** Sits flush with the top edge of the screen. */
		notch?: Snippet;
		/** Desktop contents. Falls back to abstract windows. */
		children?: Snippet;
		/** Slightly taller mock with more ambient detail. */
		tall?: boolean;
		class?: string;
	}

	let { notch, children, tall = false, class: className = '' }: Props = $props();

	const clock = '9:41';
</script>

<div class="screen {className}" class:tall>
	<div class="screen__menubar" aria-hidden="true">
		<span class="screen__lights">
			<i style:background="#ff5f57"></i>
			<i style:background="#febc2e"></i>
			<i style:background="#28c840"></i>
		</span>
		<span class="screen__menu">
			<span class="screen__brand"><Icon name="logo" size={13} /></span>
			<span>File</span><span>Edit</span><span>View</span><span>Window</span>
		</span>
		<span class="screen__status">
			<Icon name="tray" size={13} />
			<Icon name="shield" size={13} />
			<i class="screen__battery"></i>
			<span class="screen__clock">{clock}</span>
		</span>
	</div>

	<div class="screen__desktop">
		{#if children}
			{@render children()}
		{:else}
			<div class="mock-window mock-window--a" aria-hidden="true">
				<div class="mock-window__bar"><span></span><span></span><span></span></div>
				<div class="mock-lines">
					<i style:width="72%"></i><i style:width="54%"></i><i style:width="63%"></i
					><i style:width="38%"></i>
				</div>
			</div>
			<div class="mock-window mock-window--b" aria-hidden="true">
				<div class="mock-window__bar"><span></span><span></span></div>
				<div class="mock-grid">
					<i></i><i></i><i></i><i></i><i></i><i></i>
				</div>
			</div>
		{/if}
	</div>

	{#if notch}
		<div class="screen__notch">{@render notch()}</div>
	{/if}
</div>

<style>
	.screen {
		position: relative;
		border-radius: var(--r-lg);
		border: 1px solid rgb(15 23 42 / 0.1);
		background: #0b0f19;
		box-shadow: var(--shadow-3);
		overflow: hidden;
		isolation: isolate;
	}

	/* Screen glass: a soft diagonal sheen across the whole display. */
	.screen::after {
		content: '';
		position: absolute;
		inset: 0;
		pointer-events: none;
		background: rgb(255 255 255 / 0.03);
		z-index: 3;
	}

	.screen__menubar {
		position: relative;
		z-index: 2;
		display: flex;
		align-items: center;
		gap: 1rem;
		height: 30px;
		padding-inline: 0.75rem;
		background: rgb(255 255 255 / 0.03);
		border-bottom: 1px solid rgb(255 255 255 / 0.06);
		font-size: 10.5px;
		color: rgb(148 163 184 / 0.85);
	}

	.screen__lights {
		display: flex;
		gap: 5px;
		flex: none;
	}

	.screen__lights i {
		width: 9px;
		height: 9px;
		border-radius: 50%;
		opacity: 0.85;
	}

	.screen__menu {
		display: flex;
		align-items: center;
		gap: 0.85rem;
		flex: 1;
		justify-content: center;
		white-space: nowrap;
		overflow: hidden;
	}

	.screen__brand {
		color: rgb(226 232 240 / 0.9);
		display: inline-flex;
	}

	.screen__status {
		display: flex;
		align-items: center;
		gap: 0.5rem;
		flex: none;
		color: rgb(148 163 184 / 0.7);
	}

	.screen__battery {
		width: 17px;
		height: 8px;
		border-radius: 2px;
		border: 1px solid rgb(148 163 184 / 0.55);
		position: relative;
	}

	.screen__battery::after {
		content: '';
		position: absolute;
		inset: 1px;
		width: 9px;
		border-radius: 1px;
		background: rgb(148 163 184 / 0.8);
	}

	.screen__clock {
		font-variant-numeric: tabular-nums;
	}

	.screen__desktop {
		position: relative;
		height: clamp(190px, 30vw, 340px);
		background: var(--accent-soft);
	}

	.screen.tall .screen__desktop {
		height: clamp(260px, 40vw, 460px);
	}

	.screen__notch {
		position: absolute;
		top: 0;
		left: 0;
		right: 0;
		z-index: 4;
		display: flex;
		justify-content: center;
		/* Size container for the notch mock: one `cqw` is 1% of the screen. */
		container-type: inline-size;
	}

	.mock-window {
		position: absolute;
		border-radius: 12px;
		border: 1px solid rgb(255 255 255 / 0.07);
		background: rgb(15 20 31 / 0.55);
		backdrop-filter: blur(10px);
		overflow: hidden;
	}

	.mock-window--a {
		left: 6%;
		top: 22%;
		width: 46%;
		padding-bottom: 1rem;
	}

	.mock-window--b {
		right: 7%;
		top: 38%;
		width: 34%;
		padding-bottom: 0.8rem;
	}

	.mock-window__bar {
		display: flex;
		gap: 5px;
		padding: 8px 10px;
		border-bottom: 1px solid rgb(255 255 255 / 0.05);
	}

	.mock-window__bar span {
		width: 6px;
		height: 6px;
		border-radius: 50%;
		background: rgb(255 255 255 / 0.14);
	}

	.mock-lines {
		display: flex;
		flex-direction: column;
		gap: 7px;
		padding: 12px 12px 4px;
	}

	.mock-lines i {
		height: 5px;
		border-radius: 99px;
		background: rgb(255 255 255 / 0.09);
	}

	.mock-grid {
		display: grid;
		grid-template-columns: repeat(3, 1fr);
		gap: 7px;
		padding: 12px;
	}

	.mock-grid i {
		aspect-ratio: 1;
		border-radius: 6px;
		background: rgb(255 255 255 / 0.07);
	}

	@media (max-width: 620px) {
		.screen__menu span:not(.screen__brand) {
			display: none;
		}
	}
</style>
