<script lang="ts">
	import { stats } from '$data/site';
	import { stagger } from '$lib/actions/reveal';
	import { prefersReducedMotion } from '$lib/motion/gsap';

	let listEl: HTMLUListElement | undefined = $state();

	// Count-up each number the first time the strip scrolls into view. Parsed
	// from the content module so the target can never drift from the copy.
	const targets = stats.map((stat) => Number.parseInt(String(stat.value), 10) || 0);
	const raf: number[] = [];

	$effect(() => {
		const node = listEl;
		if (!node) return;

		const values = node.querySelectorAll<HTMLElement>('[data-count]');
		if (values.length === 0) return;

		const reduced = prefersReducedMotion();
		if (reduced) return;

		let done = false;
		const observer = new IntersectionObserver(
			(entries) => {
				if (!entries.some((entry) => entry.isIntersecting)) return;
				observer.disconnect();
				if (done) return;
				done = true;

				let start: number | null = null;
				const DURATION = 1400;

				const frame = (now: number) => {
					if (start === null) start = now;
					const progress = Math.min(1, (now - start) / DURATION);
					// Ease-out cubic, like the rest of the page's motion language.
					const eased = 1 - Math.pow(1 - progress, 3);
					values.forEach((el, index) => {
						const target = targets[index] ?? 0;
						el.textContent = String(Math.round(target * eased));
					});
					if (progress < 1) raf.push(requestAnimationFrame(frame));
					else values.forEach((el, index) => (el.textContent = String(targets[index])));
				};
				raf.push(requestAnimationFrame(frame));
			},
			{ rootMargin: '0px 0px -10% 0px', threshold: 0.2 }
		);
		observer.observe(node);

		return () => {
			observer.disconnect();
			while (raf.length) {
				const id = raf.pop();
				if (id) cancelAnimationFrame(id);
			}
		};
	});
</script>

<section class="section-tight stats-section" aria-label="QuickNotch at a glance">
	<div class="container">
		<ul class="stats" bind:this={listEl} use:stagger={{ step: 0.09 }} data-stagger>
			{#each stats as stat, index (stat.label)}
				<li class="stat">
					<span class="stat__value"><span data-count>{stat.value}</span><span class="stat__plus">×</span></span>
					<span class="stat__label">{stat.label}</span>
					<span class="stat__detail">{stat.detail}</span>
				</li>
			{/each}
		</ul>
	</div>
</section>

<style>
	.stats-section {
		position: relative;
	}

	/* A notice stamped on the paper, like an edition number. */
	.stats-section::before {
		content: 'the numbers, in case anyone asks';
		position: absolute;
		top: 1.25rem;
		right: var(--gutter);
		font-family: var(--font-mono);
		font-size: var(--fs-micro);
		letter-spacing: 0.18em;
		text-transform: uppercase;
		color: var(--ink-3);
		opacity: 0.75;
	}

	.stats {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(min(100%, 13rem), 1fr));
		gap: clamp(0.85rem, 1.8vw, 1.4rem);
	}

	.stat {
		display: flex;
		flex-direction: column;
		gap: 0.35rem;
		padding: clamp(1.25rem, 2.4vw, 1.9rem);
		border-radius: var(--r-lg);
		border: 2px solid var(--ink);
		background: var(--paper-lift);
		box-shadow: 5px 5px 0 rgb(33 28 19 / 0.9);
		transition: transform var(--dur) var(--ease-out), box-shadow var(--dur) var(--ease-out);
	}

	/* Alternate the sticker tilt slightly so the row reads hand-placed, like a
	   poster that was stuck down one tile at a time. */
	.stat:nth-child(1) {
		rotate: -0.7deg;
	}

	.stat:nth-child(2) {
		rotate: 0.5deg;
	}

	.stat:nth-child(3) {
		rotate: -0.4deg;
	}

	.stat:nth-child(4) {
		rotate: 0.7deg;
	}

	.stat:hover {
		transform: translate(-2px, -2px) rotate(0deg);
		box-shadow: 7px 7px 0 rgb(33 28 19 / 0.9);
	}

	.stat__value {
		display: inline-flex;
		align-items: baseline;
		gap: 0.15em;
		font-family: var(--font-display);
		font-size: clamp(2.4rem, 4.6vw, 3.4rem);
		font-weight: 800;
		letter-spacing: -0.05em;
		line-height: 1;
		color: var(--accent-text);
		font-variant-numeric: tabular-nums;
	}

	/* The constant sits in as the "×" unit stamp, like a price heel. */
	.stat__plus {
		font-size: 0.5em;
		color: var(--ink-3);
		letter-spacing: 0;
	}

	.stat__label {
		font-family: var(--font-display);
		font-size: 1.05rem;
		font-weight: 700;
		color: var(--text);
	}

	.stat__detail {
		font-size: var(--fs-small);
		color: var(--text-3);
		line-height: 1.5;
	}
</style>