<script lang="ts">
	import Icon from './Icon.svelte';
	import NotchPanel from './NotchPanel.svelte';
	import type { TourStepId } from '$data/site';

	/**
	 * The laptop the tour happens on.
	 *
	 * Real CSS 3D, not a picture: a lid on a hinge over a base, so the scroll can
	 * rotate it into place and lift the lid, with the notch already living on the
	 * screen behind it. The screen holds the actual `NotchPanel` markup, which is
	 * why the tour can show the app's own geometry rather than an illustration.
	 *
	 * The lid's angle, the device's rotation and its settle are written by
	 * `TourSection` as CSS variables on `.device` / `.lid` — per-frame values must
	 * never round-trip through Svelte state. Everything here is decorative: the
	 * captions beside it carry the meaning, so the whole device is hidden from
	 * assistive tech.
	 */

	interface Props {
		state: TourStepId;
		shape?: 'amoled' | 'pill' | 'handle';
	}

	let { state, shape = 'amoled' }: Props = $props();

	const keys = [13, 13, 12, 11];
</script>

<div class="device-stage" aria-hidden="true">
	<div class="device" data-state={state}>
		<div class="lid">
			<div class="lid__face lid__face--front">
				<div class="bezel">
					<div class="screen" data-state={state}>
						<div class="wall">
							<span class="wall__blob wall__blob--a"></span>
							<span class="wall__blob wall__blob--b"></span>
							<span class="window window--a">
								<span class="window__bar"><i></i><i></i><i></i></span>
								<span class="window__lines"><i></i><i></i><i></i><i></i></span>
							</span>
							<span class="window window--b">
								<span class="window__bar"><i></i><i></i></span>
								<span class="window__grid"><i></i><i></i><i></i><i></i></span>
							</span>
						</div>

						<!-- The game the HUD is sitting over: flat ground, two blurred
						     lights. Only painted for the gaming step. -->
						<div class="game">
							<span class="game__light game__light--a"></span>
							<span class="game__light game__light--b"></span>
							<span class="game__reticle"></span>
						</div>

						<div class="screen__menu">
							<span class="screen__brand"><Icon name="logo" size={11} /></span>
							<span>File</span><span>Edit</span><span>View</span>
							<span class="screen__spacer"></span>
							<span class="screen__clock">2:52 pm</span>
							<Icon name="battery" size={11} />
						</div>

						<div class="screen__notch">
							<NotchPanel {state} {shape} />
						</div>
					</div>
				</div>
				<span class="bezel__camera"></span>
			</div>

			<div class="lid__face lid__face--back">
				<span class="lid__logo"><Icon name="logo" size={26} /></span>
				<span class="lid__word mono">quicknotch</span>
			</div>
		</div>

		<div class="base">
			<div class="deck">
				<div class="keys">
					{#each keys as count, row (row)}
						<span class="keys__row">
							{#each Array(count) as _, key (key)}
								<i></i>
							{/each}
						</span>
					{/each}
					<span class="keys__row keys__row--space">
						<i></i>
					</span>
				</div>
				<span class="trackpad"></span>
			</div>
			<span class="base__lip"></span>
		</div>
	</div>

	<span class="device__shadow"></span>
</div>

<style>
	.device-stage {
		position: relative;
		width: 100%;
		/* Room for the base to swing toward the viewer without clipping, and for
		   the lid to land when it is closed. */
		padding-block: 2% 10%;
		container-type: inline-size;
		perspective: 2000px;
		perspective-origin: 50% 44%;
	}

	.device {
		position: relative;
		width: 100%;
		aspect-ratio: 1.5;
		margin-inline: auto;
		transform-style: preserve-3d;
		transform: translate3d(var(--tx, 0%), var(--ty, 0), 0) rotateX(var(--rx, 16deg)) rotateY(var(--ry, -30deg))
			scale(var(--sc, 0.9));
		will-change: transform;
	}

	/* ---- Lid ------------------------------------------------------------- */
	.lid {
		position: absolute;
		inset: 0;
		transform-origin: 50% 100%;
		transform: rotateX(var(--lid, 6deg));
		transform-style: preserve-3d;
		will-change: transform;
	}

	.lid__face {
		position: absolute;
		inset: 0;
		border-radius: 2.4cqw;
		backface-visibility: hidden;
	}

	.lid__face--front {
		transform: translateZ(1px);
		background: #14100a;
		box-shadow: inset 0 0 0 1px rgb(255 255 255 / 0.08);
	}

	.lid__face--back {
		transform: translateZ(-1px) rotateY(180deg);
		display: grid;
		place-items: center;
		gap: 0.4rem;
		background: #17130c;
		border: 2px solid var(--ink);
		box-shadow: inset 0 0 0 1px rgb(255 255 255 / 0.06);
		border-radius: 2.4cqw;
	}

	.lid__logo {
		display: grid;
		place-items: center;
		color: var(--tomato);
		opacity: 0.9;
	}

	.lid__word {
		font-size: 0.62rem;
		letter-spacing: 0.3em;
		color: var(--ink-3);
		opacity: 0.55;
	}

	.bezel {
		position: absolute;
		inset: 1.7%;
		padding: 0.9%;
		border-radius: 1.7cqw;
		background: #080604;
		box-shadow: inset 0 0 0 1px rgb(255 255 255 / 0.07);
	}

	.bezel__camera {
		position: absolute;
		top: 0.75%;
		left: 50%;
		width: 0.45%;
		aspect-ratio: 1;
		border-radius: 50%;
		background: #2c2a26;
		box-shadow: inset 0 0 0 1px rgb(255 255 255 / 0.12);
		translate: -50% 0;
	}

	/* ---- Screen ---------------------------------------------------------- */
	/* The screen is the size container every `cqw` inside the notch reads from. */
	.screen {
		position: relative;
		height: 100%;
		border-radius: 1.1cqw;
		overflow: hidden;
		background: #0d1219;
		container-type: inline-size;
	}

	.wall {
		position: absolute;
		inset: 0;
		overflow: hidden;
		transition: opacity 420ms var(--ease-out);
	}

	.wall__blob {
		position: absolute;
		border-radius: 50%;
		filter: blur(70px);
		opacity: 0.3;
	}

	.wall__blob--a {
		left: -8%;
		top: 10%;
		width: 58%;
		height: 70%;
		background: #1d3f66;
	}

	.wall__blob--b {
		right: -6%;
		bottom: -10%;
		width: 46%;
		height: 62%;
		background: #6f2c14;
	}

	.window {
		position: absolute;
		border-radius: 8px;
		border: 1px solid rgb(255 255 255 / 0.1);
		background: rgb(13 18 27 / 0.75);
		overflow: hidden;
	}

	.window--a {
		left: 7%;
		top: 26%;
		width: 38%;
		padding-bottom: 1rem;
	}

	.window--b {
		right: 8%;
		top: 40%;
		width: 28%;
		padding-bottom: 0.8rem;
	}

	.window__bar {
		display: flex;
		gap: 4px;
		padding: 6px 8px;
		border-bottom: 1px solid rgb(255 255 255 / 0.05);
	}

	.window__bar i {
		width: 5px;
		height: 5px;
		border-radius: 50%;
		background: rgb(255 255 255 / 0.16);
	}

	.window__lines {
		display: flex;
		flex-direction: column;
		gap: 6px;
		padding: 9px;
	}

	.window__lines i {
		height: 4px;
		border-radius: 99px;
		background: rgb(255 255 255 / 0.1);
	}

	.window__lines i:nth-child(2) {
		width: 72%;
	}

	.window__lines i:nth-child(3) {
		width: 86%;
	}

	.window__lines i:nth-child(4) {
		width: 48%;
	}

	.window__grid {
		display: grid;
		grid-template-columns: repeat(2, 1fr);
		gap: 6px;
		padding: 9px;
	}

	.window__grid i {
		aspect-ratio: 1;
		border-radius: 5px;
		background: rgb(255 255 255 / 0.08);
	}

	/* ---- The game backdrop ----------------------------------------------- */
	.game {
		position: absolute;
		inset: 0;
		opacity: 0;
		overflow: hidden;
		background: #0a0f15;
		transition: opacity 420ms var(--ease-out);
	}

	.screen[data-state='gaming'] .game {
		opacity: 1;
	}

	.screen[data-state='gaming'] .wall {
		opacity: 0;
	}

	.game__light {
		position: absolute;
		border-radius: 50%;
		filter: blur(50px);
	}

	.game__light--a {
		left: 6%;
		top: -12%;
		width: 62%;
		height: 92%;
		background: #2a6f8f;
		opacity: 0.85;
	}

	.game__light--b {
		right: 4%;
		bottom: -18%;
		width: 54%;
		height: 84%;
		background: #b3451f;
		opacity: 0.9;
	}

	.game__reticle {
		position: absolute;
		left: 50%;
		top: 58%;
		width: 8%;
		aspect-ratio: 1;
		translate: -50% -50%;
		border-radius: 50%;
		border: 1px solid rgb(245 242 234 / 0.35);
	}

	.screen__menu {
		position: relative;
		z-index: 2;
		display: flex;
		align-items: center;
		gap: 1.1cqw;
		height: 3.6cqw;
		padding-inline: 1.4cqw;
		background: rgb(255 255 255 / 0.04);
		border-bottom: 1px solid rgb(255 255 255 / 0.06);
		font-size: 1.1cqw;
		color: rgb(148 163 184 / 0.85);
		white-space: nowrap;
	}

	.screen__brand {
		display: inline-flex;
		color: var(--tomato);
	}

	.screen__spacer {
		flex: 1;
	}

	.screen__clock {
		font-variant-numeric: tabular-nums;
	}

	.screen__notch {
		position: absolute;
		inset: 0 0 auto;
		z-index: 3;
	}

	/* ---- Base ------------------------------------------------------------ */
	/* The deck rotates forward out of the hinge, so it reads as the foreground of
	   a laptop seen from the front — the lid rises out of its near edge. */
	.base {
		position: absolute;
		top: 100%;
		left: 0;
		left: 3%;
		width: 94%;
		height: 58%;
		transform-origin: 50% 0;
		transform: rotateX(90deg);
		transform-style: preserve-3d;
		border-radius: 1.6cqw;
		background: #171209;
		border: 2px solid var(--ink);
	}

	.deck {
		position: absolute;
		inset: 7% 6% 10%;
		display: flex;
		flex-direction: column;
		gap: 2.2%;
	}

	.keys {
		display: flex;
		flex-direction: column;
		gap: 9%;
		flex: 1;
	}

	.keys__row {
		display: flex;
		gap: 1%;
		justify-content: center;
		flex: 1;
	}

	.keys__row i {
		flex: 1 1 0;
		max-width: 6.4%;
		border-radius: 2px;
		background: #2b2418;
		box-shadow:
			inset 0 1px 0 rgb(255 255 255 / 0.07),
			inset 0 -1px 0 rgb(0 0 0 / 0.35);
	}

	.keys__row--space i {
		flex: 0 0 42%;
		max-width: none;
	}

	.trackpad {
		display: block;
		align-self: center;
		width: 32%;
		height: 26%;
		margin-top: 2%;
		border-radius: 0.5rem;
		border: 1px solid rgb(255 255 255 / 0.06);
		background: #1c1710;
	}

	.base__lip {
		position: absolute;
		inset: auto 0 0;
		height: 8%;
		border-radius: 0 0 1.6cqw 1.6cqw;
		background: #0e0b06;
	}

	/* ---- Ground shadow --------------------------------------------------- */
	.device__shadow {
		position: absolute;
		left: 50%;
		bottom: 22%;
		width: 62%;
		height: 6%;
		border-radius: 50%;
		background: rgb(33 28 19 / 0.55);
		filter: blur(26px);
		translate: -50% 0;
		opacity: var(--shadow-opacity, 0.4);
		pointer-events: none;
	}

	@media (max-width: 720px) {
		.device {
			width: 100%;
		}

		.device-stage {
			perspective: 1200px;
		}
	}
</style>
