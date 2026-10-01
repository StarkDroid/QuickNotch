<script lang="ts">
	import AppTile from './AppTile.svelte';
	import Icon from './Icon.svelte';
	import { dockTiles } from '$data/site';

	/**
	 * The signature QuickNotch interaction, live on the page: a slim notch hangs
	 * off the top edge of the desktop, and the moment your cursor visits it the
	 * overlay answers — growing down into an app dock, tiles magnetising toward
	 * the cursor in a gentle cosine-squared wave. Leave the overlay and it tucks
	 * itself back into the notch. That is exactly how the desktop product behaves.
	 *
	 * Hover behaviour only arms on fine pointers; touch users tap the notch to
	 * open (Enter/Space works from the keyboard) and a tile to launch — it closes
	 * again either way, so nothing is ever locked open. The collapsed notch always
	 * renders, so nothing is hidden without JavaScript — the wave just never arms.
	 */

	let open = $state(false);
	let launched = $state(false);
	let launchedName = $state('');
	let clock = $state('9:41');
	let reduced = $state(false);

	const tiles = dockTiles.slice(0, 7);

	// Per-tile transform state, written by the wave math and applied in a single
	// rAF batch so pointer-move never touches the DOM directly.
	const tileState = tiles.map(() => ({ scale: 1, lift: 0, x: 0 }));
	// Applied (display) values, lerped toward `tileState` so the wave flows.
	const display = tiles.map(() => ({ scale: 1, lift: 0, x: 0 }));

	let overlayEl: HTMLElement | undefined;
	let raf = 0;
	let running = false;
	let pointerX: number | null = null;
	let graceTimer = 0;
	let settleTimer = 0;
	let launchTimer = 0;
	/** Non-null only while the cursor actually rests over the overlay. */
	let overOverlay = false;

	const MAX_SCALE = 1.28;
	const MAX_LIFT = 16;
	const WAVE_RADIUS_BASES = 2.1;
	const SPREAD = 0.22;

	$effect(() => {
		reduced =
			typeof window !== 'undefined' &&
			window.matchMedia('(prefers-reduced-motion: reduce)').matches;
	});

	/** The wave only fronts for real hovering pointers. */
	function finePointer(): boolean {
		return (
			typeof window !== 'undefined' &&
			window.matchMedia('(hover: hover) and (pointer: fine)').matches
		);
	}

	// A live ticking clock inside the resting notch — a nod to the real product's
	// idle pill showing the time. Wired up only while mounted and paused entirely
	// under reduced motion.
	$effect(() => {
		if (typeof window === 'undefined') return;
		let frame: number | null = null;
		let last = performance.now();

		const tick = (now: number) => {
			frame = requestAnimationFrame(tick);
			if (now - last < 1000) return;
			last = now;
			const d = new Date();
			clock = [d.getHours(), d.getMinutes()].map((n) => String(n).padStart(2, '0')).join(':');
		};
		tick(performance.now());
		return () => {
			if (frame !== null) cancelAnimationFrame(frame);
		};
	});

	function armWave() {
		if (reduced) return;
		running = true;
		if (!raf) raf = requestAnimationFrame(frame);
	}

	function stopWave() {
		running = false;
		if (raf) cancelAnimationFrame(raf);
		raf = 0;
	}

	/** Cosine-squared falloff: 1 under the cursor, 0 at the wave's edge. */
	function waveIntensity(dist: number, radius: number): number {
		const clamped = Math.min(dist, radius) / radius;
		const cos = Math.cos(clamped * (Math.PI / 2));
		return cos * cos;
	}

	/** Recompute each tile's resting wave state for the current cursor. */
	function updateWave() {
		if (pointerX === null) {
			for (const s of tileState) {
				s.scale = 1;
				s.lift = 0;
				s.x = 0;
			}
			return;
		}

		const tilesEl = overlayEl?.querySelectorAll<HTMLElement>('[data-tile]');
		if (!tilesEl || tilesEl.length === 0) return;

		const base = tilesEl[0]?.getBoundingClientRect().width ?? 40;
		const radius = base * WAVE_RADIUS_BASES;
		const dockRect = overlayEl!.getBoundingClientRect();
		const dockCentre = dockRect.left + dockRect.width / 2;

		const centers: number[] = [];
		tilesEl.forEach((el) => {
			const r = el.getBoundingClientRect();
			centers.push(r.left + r.width / 2);
		});

		tilesEl.forEach((el, index) => {
			const intensity = waveIntensity(Math.abs(centers[index] - pointerX!), radius);
			const scale = 1 + (MAX_SCALE - 1) * intensity;
			tileState[index].scale = scale;
			tileState[index].lift = MAX_LIFT * intensity;
			// Tiles push outward from the cursor so magnified neighbours make
			// room instead of overlapping — the classic oil-spill spread.
			const away = Math.sign(centers[index] - pointerX!) || (centers[index] < dockCentre ? -1 : 1);
			tileState[index].x = away * (scale - 1) * base * SPREAD;
		});
	}

	function frame() {
		if (!running) return;
		const tilesEl = overlayEl?.querySelectorAll<HTMLElement>('[data-tile]');
		if (!tilesEl) {
			stopWave();
			return;
		}

		let settled = true;
		tilesEl.forEach((el, index) => {
			const target = tileState[index];
			const cur = display[index];
			const k = pointerX === null ? 0.2 : 0.3;
			cur.scale += (target.scale - cur.scale) * k;
			cur.lift += (target.lift - cur.lift) * k;
			cur.x += (target.x - cur.x) * k;
			el.style.transform = `translate3d(${cur.x.toFixed(1)}px, ${-cur.lift.toFixed(1)}px, 0) scale(${cur.scale.toFixed(3)})`;

			if (
				Math.abs(cur.scale - target.scale) > 0.002 ||
				Math.abs(cur.lift - target.lift) > 0.1 ||
				Math.abs(cur.x - target.x) > 0.1
			) {
				settled = false;
			}
		});

		if (settled && (pointerX === null || !overOverlay)) {
			stopWave();
			return;
		}
		raf = requestAnimationFrame(frame);
	}

	function onEnter() {
		if (!finePointer()) return;
		clearGrace();
		clearTimeout(settleTimer);
		if (!open) {
			open = true;
			// Let the island settle before the wave arms so tiles stay put during
			// the grow-in transition.
			window.setTimeout(() => {
				if (open) armWave();
			}, 90);
		} else {
			armWave();
		}
	}

	function onMove(event: PointerEvent) {
		if (!open || reduced) return;
		overOverlay = true;
		pointerX = event.clientX;
		updateWave();
		armWave();
	}

	function onLeave() {
		if (!finePointer()) return;
		overOverlay = false;
		pointerX = null;
		// A short grace period lets quick trips across the edge avoid flapping;
		// re-entering cancels the collapse.
		graceTimer = window.setTimeout(() => {
			open = false;
			graceTimer = 0;
			updateWave();
			armWave();
		}, 160);
	}

	function clearGrace() {
		if (graceTimer) {
			clearTimeout(graceTimer);
			graceTimer = 0;
		}
	}

	/** Coarse pointers (and keyboard users via the button): tap to toggle. */
	function onToggle() {
		if (finePointer()) return;
		clearGrace();
		if (open) {
			open = false;
			pointerX = null;
			updateWave();
		} else {
			open = true;
			if (!reduced) armWave();
		}
	}

	function onTileClick(index: number, event: MouseEvent) {
		event.stopPropagation();
		clearTimeout(launchTimer);
		launchedName = tiles[index].name;
		launched = true;
		launchTimer = window.setTimeout(() => (launched = false), 1800);

		// Let the toast land for a beat, then the island tucks away — launching
		// dismisses the overlay, exactly like the product.
		clearGrace();
		clearTimeout(settleTimer);
		settleTimer = window.setTimeout(() => {
			open = false;
			pointerX = null;
			updateWave();
			armWave();
		}, 720);
	}

	$effect(() => {
		return () => {
			clearGrace();
			clearTimeout(settleTimer);
			clearTimeout(launchTimer);
			stopWave();
		};
	});
</script>

<svelte:window onkeydown={(event) => event.key === 'Escape' && (open = false)} />

<div
	class="dock-demo"
	role="region"
	aria-label="Live QuickNotch overlay demo. Hover the notch at the top of the screen to expand it into a dock, then move your cursor across the icons to feel the magnification wave. It tucks back into the notch when you leave."
>
	<div class="dock-demo__screen">
		<div class="dock-demo__chrome" aria-hidden="true">
			<i></i><i></i><i></i>
			<span>File</span><span>Edit</span><span>View</span>
		</div>

		<div class="dock-demo__desktop" aria-hidden="true">
			<div class="dock-demo__window dock-demo__window--a">
				<div class="dock-demo__winbar"><i></i><i></i><i></i></div>
				<div class="dock-demo__winlines"><i></i><i></i><i></i><i></i></div>
			</div>
			<div class="dock-demo__window dock-demo__window--b">
				<div class="dock-demo__winbar"><i></i><i></i></div>
				<div class="dock-demo__wingrid"><i></i><i></i><i></i><i></i></div>
			</div>

			<!-- The overlay: one connected shape — resting notch, then island. -->
			<div
				class="dock-demo__overlay"
				class:open
				role="group"
				bind:this={overlayEl}
				onpointerenter={onEnter}
				onpointermove={onMove}
				onpointerleave={onLeave}
			>
				<button
					type="button"
					class="dock-demo__notch"
					class:notch-open={open}
					aria-label={open
						? 'QuickNotch overlay is open, press Escape to close'
						: 'Notch, hover or click to open QuickNotch'}
					aria-expanded={open}
					onclick={onToggle}
				>
					<span class="dock-demo__notch-icon"><Icon name="logo" size={14} /></span>
					<span class="dock-demo__clock">{clock}</span>
				</button>

				<div class="dock-demo__dock" aria-hidden={!open} inert={!open}>
					{#each tiles as tile, index (tile.id)}
						<button
							type="button"
							class="dock-demo__tile-btn"
							data-tile
							aria-label="{tile.name}, launch demo"
							onclick={(event) => onTileClick(index, event)}
						>
							<AppTile {tile} size={40} decorative />
						</button>
					{/each}
				</div>

				{#if launchedName}
					<div class="dock-demo__launched" class:show={launched} aria-live="polite">
						<Icon name="check" size={14} />
						{launchedName} opened
					</div>
				{/if}
			</div>
		</div>
	</div>

	<p class="dock-demo__hint">
		<span class="dock-demo__hint-step"><i class="dot" aria-hidden="true"></i>Hover the notch</span>
		<Icon name="arrow-right" size={14} />
		<span class="dock-demo__hint-step"><i class="dot" aria-hidden="true"></i>Overlay pops open · move to feel the wave</span>
		<Icon name="arrow-right" size={14} />
		<span class="dock-demo__hint-step"><i class="dot" aria-hidden="true"></i>Leave, it tucks away</span>
	</p>
</div>

<style>
	.dock-demo {
		position: relative;
		max-width: 52rem;
		margin-inline: auto;
		z-index: 1;
	}

	.dock-demo__screen {
		position: relative;
		border-radius: var(--r-lg);
		border: 1px solid var(--line);
		background: #0b0f19;
		box-shadow: var(--shadow-3);
		overflow: hidden;
		height: clamp(280px, 40vw, 420px);
	}

	.dock-demo__chrome {
		position: relative;
		z-index: 2;
		display: flex;
		align-items: center;
		gap: 0.7rem;
		height: 34px;
		padding-inline: 0.85rem;
		background: rgb(255 255 255 / 0.03);
		border-bottom: 1px solid rgb(255 255 255 / 0.06);
		font-size: 10.5px;
		color: rgb(148 163 184 / 0.85);
	}

	.dock-demo__chrome i {
		width: 9px;
		height: 9px;
		border-radius: 50%;
		background: rgb(255 255 255 / 0.18);
	}

	.dock-demo__chrome span {
		margin-inline: 0.35rem;
	}

	.dock-demo__desktop {
		position: absolute;
		inset: 34px 0 0;
		background: rgb(12 15 24 / 0.9);
		overflow: hidden;
	}

	/* A soft flat glow behind the overlay — flat washes only, no gradient. */
	.dock-demo__desktop::before {
		content: '';
		position: absolute;
		left: 50%;
		top: -80px;
		translate: -50% 0;
		width: 560px;
		height: 320px;
		background: var(--accent-soft);
		filter: blur(70px);
		opacity: 0.7;
	}

	.dock-demo__window {
		position: absolute;
		border-radius: 12px;
		border: 1px solid rgb(255 255 255 / 0.07);
		background: rgb(15 20 31 / 0.65);
		backdrop-filter: blur(10px);
		overflow: hidden;
	}

	.dock-demo__window--a {
		left: 7%;
		top: 24%;
		width: 44%;
		padding-bottom: 0.9rem;
	}

	.dock-demo__window--b {
		right: 8%;
		top: 46%;
		width: 32%;
		padding-bottom: 0.7rem;
	}

	.dock-demo__winbar {
		display: flex;
		gap: 5px;
		padding: 8px 10px;
		border-bottom: 1px solid rgb(255 255 255 / 0.05);
	}

	.dock-demo__winbar i {
		width: 6px;
		height: 6px;
		border-radius: 50%;
		background: rgb(255 255 255 / 0.14);
	}

	.dock-demo__winlines {
		display: flex;
		flex-direction: column;
		gap: 7px;
		padding: 12px 12px 4px;
	}

	.dock-demo__winlines i {
		height: 5px;
		border-radius: 99px;
		background: rgb(255 255 255 / 0.09);
	}

	.dock-demo__wingrid {
		display: grid;
		grid-template-columns: repeat(3, 1fr);
		gap: 7px;
		padding: 12px;
	}

	.dock-demo__wingrid i {
		aspect-ratio: 1;
		border-radius: 6px;
		background: rgb(255 255 255 / 0.07);
	}

	/* ---- The overlay ------------------------------------------------ */
	/* One connected shape hanging off the top edge. The notch strip is always
	   there; the dock participates in the same rounded capsule, so growing it
	   reads as an island — not something bolted on mid-screen. */
	.dock-demo__overlay {
		position: absolute;
		left: 50%;
		top: 0;
		z-index: 4;
		translate: -50% 0;
		width: clamp(196px, 30vw, 236px);
		height: 38px;
		border-radius: 0 0 18px 18px;
		border: 1px solid rgb(255 255 255 / 0.18);
		background: rgb(16 18 26 / 0.94);
		backdrop-filter: blur(16px) saturate(150%);
		-webkit-backdrop-filter: blur(16px) saturate(150%);
		box-shadow:
			inset 0 1px 0 rgb(255 255 255 / 0.1),
			0 22px 50px -24px rgb(0 0 0 / 0.85);
		color: var(--on-dark);
		overflow: hidden;
		transition:
			width 520ms var(--ease-out),
			height 520ms var(--ease-out),
			border-radius 520ms var(--ease-out);
	}

	/* Notch resting on the top edge grows into a round island when open. */
	.dock-demo__overlay.open {
		width: clamp(320px, 46vw, 380px);
		height: 148px;
		border-radius: 20px;
	}

	/* The resting notch strip: logo + live clock, and the touch/keyboard handle. */
	.dock-demo__notch {
		all: unset;
		display: flex;
		align-items: center;
		justify-content: center;
		gap: 0.55rem;
		width: 100%;
		height: 38px;
		white-space: nowrap;
		cursor: pointer;
		color: inherit;
	}

	.dock-demo__notch-icon {
		display: grid;
		place-items: center;
		flex: none;
		color: var(--tomato);
	}

	.dock-demo__notch:focus-visible {
		outline: 2px solid var(--tomato);
		outline-offset: -2px;
		border-radius: 4px;
	}

	.dock-demo__clock {
		font-family: var(--font-mono);
		font-size: 0.8rem;
		letter-spacing: 0.06em;
		color: var(--on-dark-2);
		font-variant-numeric: tabular-nums;
	}

	/* The dock that grows out of the notch. */
	.dock-demo__dock {
		display: flex;
		align-items: center;
		justify-content: center;
		gap: 0.45rem;
		height: 92px;
		padding: 0 0.85rem 0.35rem;
		opacity: 0;
		transform: translateY(10px);
		transition:
			opacity 240ms var(--ease-out),
			transform 240ms var(--ease-out);
		pointer-events: none;
	}

	.dock-demo__overlay.open .dock-demo__dock {
		opacity: 1;
		transform: none;
		pointer-events: auto;
	}

	.dock-demo__tile-btn {
		position: relative;
		background: none;
		border: none;
		padding: 0;
		line-height: 0;
		transform-origin: 50% 100%;
		transition: filter var(--dur-fast) var(--ease-out);
		cursor: pointer;
	}

	.dock-demo__tile-btn:hover {
		filter: saturate(120%) brightness(1.08);
	}

	/* Launch flash: a small toast that pops up inside the open island. */
	.dock-demo__launched {
		position: absolute;
		left: 50%;
		bottom: 0.7rem;
		display: inline-flex;
		align-items: center;
		gap: 0.45rem;
		padding: 0.42rem 0.8rem;
		border-radius: var(--r-pill);
		background: rgb(255 255 255 / 0.96);
		color: #223046;
		font-size: var(--fs-small);
		font-weight: 600;
		box-shadow: var(--shadow-2);
		translate: -50% 0;
		opacity: 0;
		transform: translateY(8px);
		transition:
			opacity 180ms var(--ease-out),
			transform 180ms var(--ease-out);
		pointer-events: none;
		white-space: nowrap;
		z-index: 2;
	}

	.dock-demo__launched.show {
		opacity: 1;
		transform: translateY(0);
	}

	.dock-demo__hint {
		display: flex;
		align-items: center;
		justify-content: center;
		gap: 0.7rem;
		margin-top: 1.1rem;
		font-size: var(--fs-small);
		color: var(--text-3);
		text-align: center;
		flex-wrap: wrap;
	}

	.dock-demo__hint :global(svg) {
		color: var(--text-3);
		flex: none;
	}

	.dock-demo__hint-step {
		display: inline-flex;
		align-items: center;
		gap: 0.45rem;
	}

	.dock-demo__hint .dot {
		width: 7px;
		height: 7px;
		border-radius: 50%;
		background: var(--accent);
	}

	@media (max-width: 620px) {
		.dock-demo__screen {
			height: 300px;
		}

		.dock-demo__overlay {
			width: 186px;
		}

		.dock-demo__overlay.open {
			width: 316px;
		}

		.dock-demo__tile-btn :global(.tile) {
			width: 34px !important;
			height: 34px !important;
		}

		.dock-demo__dock {
			gap: 0.3rem;
		}

		.dock-demo__overlay.open .dock-demo__notch-icon {
			color: var(--tomato);
		}
	}

	@media (prefers-reduced-motion: reduce) {
		.dock-demo__notch-icon {
			color: var(--on-dark-2);
		}
	}
</style>