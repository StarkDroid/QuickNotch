<script lang="ts">
	import AppTile from './AppTile.svelte';
	import Icon from './Icon.svelte';
	import { dockTiles } from '$data/site';

	/**
	 * The signature QuickNotch interaction, live on the page: a collapsed pill
	 * morphs into a dock on hover, then moving the cursor across the tiles
	 * magnifies and lifts them in a cosine-squared wave — exactly how the desktop
	 * dock tracks the cursor.
	 *
	 * The collapsed pill always renders (it is the initial state), so nothing is
	 * hidden without JavaScript; the wave simply never arms.
	 */

	let expanded = $state(false);
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

	let dockEl: HTMLElement | undefined;
	let raf = 0;
	let running = false;
	let lastPointerX: number | null = null;

	const MAX_SCALE = 1.62;
	const MAX_LIFT = 34;
	const WAVE_RADIUS_BASES = 2.4;

	$effect(() => {
		reduced =
			typeof window !== 'undefined' &&
			window.matchMedia('(prefers-reduced-motion: reduce)').matches;
	});

	// A live ticking clock inside the collapsed pill — a nod to the real product's
	// idle pill showing the time. Wired up only while the component is mounted and
	// paused entirely under reduced motion.
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

	function onExpand() {
		if (reduced || expanded) return;
		expanded = true;
		// Early settle so the enter transition lands before the wave arms.
		setTimeout(() => armWave(), 80);
	}

	function armWave() {
		running = true;
		if (!raf) raf = requestAnimationFrame(frame);
	}

	/** Cosine-squared falloff: 1 under the cursor, 0 at the wave's edge. */
	function waveIntensity(dist: number, radius: number): number {
		const clamped = Math.min(dist, radius) / radius;
		const cos = Math.cos(clamped * (Math.PI / 2));
		return cos * cos;
	}

	/** Recompute each tile's resting wave state for the current cursor position. */
	function updateWave() {
		const tilesEl = dockEl?.querySelectorAll<HTMLElement>('[data-tile]');
		if (!tilesEl || tilesEl.length === 0) return;

		const rect = dockEl!.getBoundingClientRect();

		if (lastPointerX === null) {
			for (const s of tileState) {
				s.scale = 1;
				s.lift = 0;
				s.x = 0;
			}
			return;
		}

		const base = tilesEl[0]?.getBoundingClientRect().width ?? 44;
		const radius = base * WAVE_RADIUS_BASES;
		const dockCentre = rect.left + rect.width / 2;

		const centers: number[] = [];
		tilesEl.forEach((el) => {
			const r = el.getBoundingClientRect();
			centers.push(r.left + r.width / 2);
		});

		tilesEl.forEach((el, index) => {
			const pointer = lastPointerX!;
			const intensity = waveIntensity(Math.abs(centers[index] - pointer), radius);
			tileState[index].scale = 1 + (MAX_SCALE - 1) * intensity;
			tileState[index].lift = MAX_LIFT * intensity;
			// Tiles push outward from the cursor so magnified neighbours make
			// room instead of overlapping — the classic oil-spill spread.
			const away = Math.sign(centers[index] - pointer) || (centers[index] < dockCentre ? -1 : 1);
			tileState[index].x = away * (tileState[index].scale - 1) * base * 0.3;
		});
	}

	function frame() {
		if (!running) return;
		render();
		const settled = display.every(
			(cur, i) =>
				Math.abs(cur.scale - tileState[i].scale) < 0.002 &&
				Math.abs(cur.lift - tileState[i].lift) < 0.1 &&
				Math.abs(cur.x - tileState[i].x) < 0.1
		);
		const rest = tileState.every((s) => s.scale === 1 && s.lift === 0 && s.x === 0);
		if (settled && rest && !isPointerOverDock()) {
			running = false;
			raf = 0;
			return;
		}
		raf = requestAnimationFrame(frame);
	}

	/** True while the cursor sits inside the dock area (fine pointers only). */
	function isPointerOverDock(): boolean {
		if (!expanded || lastPointerX === null || !dockEl) return false;
		const rect = dockEl!.getBoundingClientRect();
		return lastPointerX >= rect.left - 40 && lastPointerX <= rect.right + 40;
	}

	function render() {
		const tilesEl = dockEl?.querySelectorAll<HTMLElement>('[data-tile]');
		if (!tilesEl) return;

		tilesEl.forEach((el, index) => {
			const target = tileState[index];
			const cur = display[index];
			const k = lastPointerX === null ? 0.21 : 0.3;
			cur.scale += (target.scale - cur.scale) * k;
			cur.lift += (target.lift - cur.lift) * k;
			cur.x += (target.x - cur.x) * k;

			el.style.transform = `translate3d(${cur.x.toFixed(1)}px, ${-cur.lift.toFixed(1)}px, 0) scale(${cur.scale.toFixed(3)})`;
		});
	}

	function onMove(event: PointerEvent) {
		if (!expanded || reduced) return;
		lastPointerX = event.clientX;
		updateWave();
		if (!raf) {
			running = true;
			raf = requestAnimationFrame(frame);
		}
	}

	function onLeaveWindow(event?: Event) {
		if (!expanded) return;
		// Only reset when the pointer genuinely left the dock area.
		if (event && dockEl) {
			const rect = dockEl!.getBoundingClientRect();
			const x =
				event instanceof PointerEvent
					? event.clientX
					: (event as unknown as { clientX?: number }).clientX ?? 0;
			if (x >= rect.left - 40 && x <= rect.right + 40) return;
		}
		lastPointerX = null;
		updateWave();
		if (!raf) {
			running = true;
			raf = requestAnimationFrame(frame);
		}
	}

	function onTileClick(index: number) {
		launchedName = tiles[index].name;
		launched = true;
		if (typeof window !== 'undefined') {
			clearTimeout((window as unknown as { __qnLaunch?: number }).__qnLaunch);
			(window as unknown as { __qnLaunch?: number }).__qnLaunch = window.setTimeout(
				() => (launched = false),
				1400
			);
		}
	}
</script>

<svelte:window
	onpointermove={onMove}
	onpointerleave={onLeaveWindow}
	onblur={onLeaveWindow}
/>

<div
	class="dock-demo"
	class:dock-demo--open={expanded}
	bind:this={dockEl}
	role="region"
	aria-label="Live QuickNotch dock demo — hover the pill to expand it into a dock, then move your cursor across the icons to feel the magnification wave"
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
		</div>

		<!-- The pill: collapsed resting state, morphs into the dock on expand. -->
		<button
			class="dock-demo__pill"
			class:open={expanded}
			type="button"
			aria-label={expanded ? 'QuickNotch dock demo is open' : 'Expand the QuickNotch dock demo'}
			onclick={onExpand}
			onpointerenter={onExpand}
		>
			<span class="dock-demo__pill-icon"><Icon name="logo" size={15} /></span>
			<span class="dock-demo__clock">{clock}</span>
		</button>

		<!-- The expanded dock: tiles magnify and lift in a cosine-squared wave. -->
		<div class="dock-demo__dock">
			{#each tiles as tile, index (tile.id)}
				<button
					type="button"
					class="dock-demo__tile-btn"
					data-tile
					aria-label="{tile.name} — launch demo"
					onclick={() => onTileClick(index)}
				>
					<AppTile {tile} size={44} decorative />
				</button>
			{/each}

			<div class="dock-demo__launched" class:show={launched} aria-live="polite">
				<Icon name="check" size={14} />
				{launchedName} opened
			</div>
		</div>
	</div>

	<p class="dock-demo__hint">
		<span class="dock-demo__hint-step"><i class="dot" aria-hidden="true"></i>Hover the pill</span>
		<Icon name="arrow-right" size={14} />
		<span class="dock-demo__hint-step"><i class="dot" aria-hidden="true"></i>Move across to feel the wave</span>
	</p>
</div>

<style>
	.dock-demo {
		position: relative;
		max-width: 52rem;
		margin-inline: auto;
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

	.dock-demo__screen::after {
		content: '';
		position: absolute;
		inset: 0;
		background: rgb(255 255 255 / 0.03);
		pointer-events: none;
		z-index: 3;
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

	/* A soft flat glow behind the dock area — flat washes only, no gradient. */
	.dock-demo__desktop::before {
		content: '';
		position: absolute;
		left: 50%;
		top: -120px;
		translate: -50% 0;
		width: 560px;
		height: 360px;
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
		top: 20%;
		width: 44%;
		padding-bottom: 0.9rem;
	}

	.dock-demo__window--b {
		right: 8%;
		top: 40%;
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

	/* ---- The pill ---------------------------------------------------- */
	.dock-demo__pill {
		position: absolute;
		left: 50%;
		top: 0;
		z-index: 4;
		translate: -50% 40%;
		display: inline-flex;
		align-items: center;
		justify-content: center;
		gap: 0.55rem;
		height: 44px;
		padding-inline: 1.25rem;
		border-radius: 999px;
		border: 1px solid rgb(255 255 255 / 0.18);
		background: rgb(18 20 28 / 0.94);
		backdrop-filter: blur(16px) saturate(150%);
		-webkit-backdrop-filter: blur(16px) saturate(150%);
		box-shadow:
			inset 0 1px 0 rgb(255 255 255 / 0.12),
			0 22px 60px -24px rgb(0 0 0 / 0.85);
		color: var(--on-dark);
		cursor: pointer;
		transition:
			height 520ms var(--ease-out),
			padding-inline 520ms var(--ease-out),
			gap 520ms var(--ease-out),
			border-radius 520ms var(--ease-out),
			translate 520ms var(--ease-out);
	}

	.dock-demo__pill.open {
		height: 0;
		padding-inline: 0;
		gap: 0;
		border-radius: 0;
		translate: -50% 40%;
		pointer-events: none;
		color: transparent;
	}

	.dock-demo__pill-icon {
		display: grid;
		place-items: center;
		flex: none;
		color: var(--tomato);
	}

	.dock-demo__clock {
		font-family: var(--font-mono);
		font-size: 0.8rem;
		letter-spacing: 0.06em;
		color: var(--on-dark-2);
		font-variant-numeric: tabular-nums;
		white-space: nowrap;
	}

	/* ---- The dock ---------------------------------------------------- */
	.dock-demo__dock {
		position: absolute;
		left: 50%;
		top: 44%;
		z-index: 4;
		translate: -50% -50%;
		display: flex;
		align-items: center;
		justify-content: center;
		flex-wrap: nowrap;
		gap: 0.5rem;
		padding: 0.6rem 0.8rem;
		border-radius: 22px;
		border: 1px solid rgb(255 255 255 / 0.16);
		background: rgb(14 16 24 / 0.92);
		backdrop-filter: blur(20px) saturate(160%);
		-webkit-backdrop-filter: blur(20px) saturate(160%);
		box-shadow:
			inset 0 1px 0 rgb(255 255 255 / 0.1),
			0 30px 80px -30px rgb(0 0 0 / 0.9);
		opacity: 0;
		pointer-events: none;
		transform: scale(0.8, 0.4) translateY(28px);
		transition:
			transform 560ms var(--ease-out),
			opacity 380ms var(--ease-out);
	}

	.dock-demo--open .dock-demo__dock {
		opacity: 1;
		pointer-events: auto;
		transform: none;
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

	/* Launch flash: a small toast that pops above the dock. */
	.dock-demo__launched {
		position: absolute;
		left: 50%;
		bottom: calc(100% + 14px);
		display: inline-flex;
		align-items: center;
		gap: 0.45rem;
		padding: 0.45rem 0.85rem;
		border-radius: var(--r-pill);
		background: rgb(255 255 255 / 0.96);
		color: #223046;
		font-size: var(--fs-small);
		font-weight: 600;
		box-shadow: var(--shadow-2);
		opacity: 0;
		transform: translate(-50%, 8px);
		transition:
			opacity 180ms var(--ease-out),
			transform 180ms var(--ease-out);
		pointer-events: none;
		white-space: nowrap;
	}

	.dock-demo__launched.show {
		opacity: 1;
		transform: translate(-50%, 0);
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

	.dock-demo__hint :global(.marquee__dots .dot) {
		background: var(--cyan);
	}

	@media (max-width: 620px) {
		.dock-demo__screen {
			height: 300px;
		}

		.dock-demo__tile-btn :global(.tile) {
			width: 34px !important;
			height: 34px !important;
		}
	}
</style>