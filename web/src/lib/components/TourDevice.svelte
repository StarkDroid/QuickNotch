<script lang="ts">
	import Icon from './Icon.svelte';
	import NotchPanel from './NotchPanel.svelte';
	import type { TourStepId } from '$data/site';

	/**
	 * The laptop the tour happens on.
	 *
	 * Deliberately frontal at rest, skewed on arrival: the settle drives the yaw
	 * and pitch out as the lid swings up, so the laptop turns to face you as it
	 * opens. The deck matches the lid's footprint on a 90° hinge — visible while
	 * skewed, edge-on (front lip only) when frontal — and stays blank, so no
	 * angle ever shows keys.
	 *
	 * The lid angle, settle and wake are written by `TourSection` as CSS
	 * variables on the wrapper — per-frame values must never round-trip through
	 * Svelte state. Everything here is decorative: the captions beside it carry
	 * the meaning, so the whole device is hidden from assistive tech.
	 */

	interface Props {
		state: TourStepId;
		shape?: 'amoled' | 'pill' | 'handle';
	}

	let { state, shape = 'amoled' }: Props = $props();
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

		<!-- The base is the laptop's front elevation: a solid body hanging from
		     the hinge with the machine's actual thickness. It lives in the
		     device's own plane, so the same perspective that tilts the lid
		     tilts the base — a body, not a paper edge. No deck and no keys:
		     from the front there is nothing else of a laptop to see, and a
		     blank body cannot look deformed. -->
		<div class="base"></div>

		<!-- The hinge: a bar across the seam, so the lid and the body read as one
		     object rather than two planes meeting in mid-air. -->
		<span class="hinge"></span>
	</div>

	<span class="device__shadow"></span>
</div>

<style>
	.device-stage {
		position: relative;
		width: 100%;
		/* Room for the thickness strip and its shadow below the hinge. */
		padding-block: 2% 9%;
		container-type: inline-size;
		perspective: 2200px;
		perspective-origin: 50% 65%;
	}

	.device {
		position: relative;
		width: 100%;
		aspect-ratio: 16 / 10;
		margin-inline: auto;
		transform-style: preserve-3d;
		/* Skewed at first, frontal at rest: the settle drives the yaw and pitch
		   out while the lid swings up, so the laptop turns to face you as it
		   opens. The base is only a thickness strip, so there are no keys to
		   glimpse at any angle. */
		transform: translate3d(var(--tx, 5%), var(--ty, -6%), 0) rotateX(var(--rx, 26deg))
			rotateY(var(--ry, -20deg)) scale(var(--sc, 0.88));
		will-change: transform;
	}

	/* ---- Lid ------------------------------------------------------------- */
	.lid {
		position: absolute;
		inset: 0;
		transform-origin: 50% 100%;
		transform: rotateX(var(--lid, 1deg));
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
		background: #101826;
		/* 0 while the lid is down, 1 once it is up. Unset = awake (no-JS). */
		opacity: var(--wake, 1);
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
		opacity: 0.42;
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

	/* ---- Base: the front elevation of the machine -------------------------
	   A solid body hanging from the hinge, drawn in the device's own plane so
	   it shares the lid's perspective. Height is the machine's real thickness
	   (~3.4% of its width), with the lid's width, rounded lower corners and a
	   small thumb notch — the front of a closed laptop, not a sheet of paper. */
	.base {
		position: absolute;
		top: 100%;
		left: 1.5%;
		width: 97%;
		height: 3.4cqw;
		margin-top: -1px;
		border-radius: 0.4cqw 0.4cqw 1.4cqw 1.4cqw;
		background: #191410;
		box-shadow:
			inset 0 2px 0 rgb(255 255 255 / 0.07),
			inset 0 -2px 0 rgb(0 0 0 / 0.45),
			inset 0 0 0 1px rgb(33 28 19 / 0.95),
			0 3px 5px rgb(0 0 0 / 0.35);
	}

	/* Thumb notch on the front lip, like the real thing's lid-lift cut-out. */
	.base::after {
		content: '';
		position: absolute;
		left: 50%;
		bottom: 0;
		width: 7%;
		height: 42%;
		translate: -50% 0;
		border-radius: 999px 999px 0 0;
		background: #0b0804;
		box-shadow: inset 0 1px 0 rgb(0 0 0 / 0.6);
	}

	/* ---- Hinge ------------------------------------------------------------ */
	.hinge {
		position: absolute;
		left: 4%;
		top: calc(100% - 0.55cqw);
		width: 92%;
		height: 1.1cqw;
		border-radius: 999px;
		background: #0c0905;
		box-shadow:
			inset 0 1px 0 rgb(255 255 255 / 0.07),
			0 1px 2px rgb(0 0 0 / 0.6);
	}

	/* ---- Ground shadow --------------------------------------------------- */
	.device__shadow {
		position: absolute;
		left: 50%;
		bottom: 1.5%;
		width: 58%;
		height: 4.5%;
		border-radius: 50%;
		background: rgb(33 28 19 / 0.55);
		filter: blur(26px);
		translate: -50% 0;
		opacity: var(--shadow-opacity, 0.4);
		pointer-events: none;
	}

	@media (max-width: 720px) {
		.device-stage {
			perspective: 1800px;
		}
	}
</style>
