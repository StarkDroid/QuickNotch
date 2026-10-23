<script lang="ts">
	import Icon from './Icon.svelte';
	import { buyCta, pricing, site } from '$data/site';
	import { reveal, stagger } from '$lib/actions/reveal';
	import { scrollToId } from '$lib/motion/smoothScroll';

	/**
	 * The price, as a printed ticket.
	 *
	 * One payment, one ticket: the price and the buy button sit on the left stub,
	 * what the purchase covers reads on the right, and a perforated rule with two
	 * punch marks runs between them. Every number and string comes from the
	 * content module, so the card, the buttons and the structured data can never
	 * disagree about what the app costs.
	 */
</script>

<section class="section" id="pricing">
	<div class="container">
		<header
			class="section-head section-head--center"
			use:reveal={{ from: 'up' }}
			data-reveal="up"
		>
			<p class="eyebrow">{pricing.eyebrow}</p>
			<h2 class="display h2">{pricing.title}</h2>
			<p class="lead">{pricing.body}</p>
		</header>

		<div class="ticket" use:reveal={{ from: 'scale', delay: 0.08 }} data-reveal="scale">
			<span class="ticket__sticker sticker sticker--blue" aria-hidden="true">
				pay once ★ keep forever
			</span>

			<div class="ticket__stub">
				<p class="mono ticket__kicker">Lifetime licence · Windows + macOS</p>

				<p class="ticket__price">
					<span class="ticket__amount">{pricing.label}</span>
					<span class="ticket__unit">{pricing.unit}</span>
				</p>

				<p class="ticket__lede">
					One payment. Every widget, both installers, and the updates that follow.
				</p>

				<div class="ticket__cta">
					<a
						class="btn btn--primary btn--lg"
						href={site.purchaseUrl}
						target="_blank"
						rel="noopener noreferrer"
					>
						<Icon name="cart" size={18} />
						{buyCta}
					</a>
					<button class="btn btn--ghost" type="button" onclick={() => scrollToId('widgets')}>
						See the widgets
					</button>
				</div>

				<p class="mono ticket__fine">{pricing.finePrint}</p>
			</div>

			<div class="ticket__perf" aria-hidden="true">
				<span class="ticket__punch"></span>
				<span class="ticket__punch ticket__punch--low"></span>
			</div>

			<div class="ticket__includes">
				<p class="mono ticket__kicker">In the box</p>
				<ul use:stagger={{ step: 0.07 }} data-stagger>
					{#each pricing.includes as item (item)}
						<li><Icon name="check" size={15} /><span>{item}</span></li>
					{/each}
				</ul>
				<p class="mono ticket__serial" aria-hidden="true">
					no. 0001 · printed on cream · {pricing.currency}
				</p>
			</div>
		</div>
	</div>
</section>

<style>
	/* ---- The ticket ------------------------------------------------------- */
	.ticket {
		position: relative;
		display: grid;
		gap: clamp(1.6rem, 3.5vw, 2.6rem);
		padding: clamp(1.6rem, 4vw, 3rem);
		border: 2px solid var(--ink);
		border-radius: var(--r-lg);
		background-color: var(--paper-lift);
		box-shadow: var(--shadow-3);
	}

	/* A flat blue spine down the stub edge — the second spot colour doing the
	   job a gradient would do on a lesser poster, without tinting the paper. */
	.ticket::before {
		content: '';
		position: absolute;
		left: 0;
		top: clamp(0.9rem, 2.4vw, 1.6rem);
		bottom: clamp(0.9rem, 2.4vw, 1.6rem);
		width: 8px;
		border-radius: 0 3px 3px 0;
		background: var(--blue);
		pointer-events: none;
	}

	.ticket__sticker {
		position: absolute;
		top: -1.05rem;
		right: clamp(1rem, 4vw, 2.5rem);
		rotate: 1.5deg;
		z-index: 2;
	}

	.ticket__stub,
	.ticket__includes {
		position: relative;
		display: flex;
		flex-direction: column;
		gap: 0.85rem;
		min-width: 0;
	}

	.ticket__kicker {
		font-size: var(--fs-micro);
		letter-spacing: 0.16em;
		text-transform: uppercase;
		color: var(--text-3);
	}

	/* ---- Price ------------------------------------------------------------ */
	.ticket__price {
		display: flex;
		align-items: baseline;
		gap: 0.6rem;
		margin-block: 0.15rem 0.1rem;
	}

	.ticket__amount {
		font-family: var(--font-display);
		font-weight: 700;
		font-size: clamp(3.2rem, 8vw, 5.2rem);
		line-height: 0.86;
		letter-spacing: -0.045em;
		color: var(--text);
		font-variant-numeric: tabular-nums;
	}

	.ticket__unit {
		font-family: var(--font-mono);
		font-size: var(--fs-micro);
		letter-spacing: 0.18em;
		text-transform: uppercase;
		color: var(--accent-text);
	}

	.ticket__lede {
		max-width: 34ch;
		font-size: var(--fs-body);
		color: var(--text-2);
	}

	.ticket__cta {
		display: flex;
		flex-wrap: wrap;
		gap: 0.75rem;
		margin-top: 0.35rem;
	}

	.ticket__fine {
		margin-top: 0.15rem;
		font-size: var(--fs-micro);
		letter-spacing: 0.08em;
		color: var(--text-3);
	}

	/* ---- Perforation ------------------------------------------------------ */
	.ticket__perf {
		position: relative;
		border-left: 2px dashed var(--line-strong);
	}

	/* Punch marks sitting on the dashed rule, like a ticket that has already
	   been stamped at the door. */
	.ticket__punch {
		position: absolute;
		left: 50%;
		top: 8%;
		translate: -50% 0;
		width: 20px;
		height: 20px;
		border-radius: 50%;
		border: 2px solid var(--ink);
		background: var(--paper-lift);
	}

	.ticket__punch--low {
		top: auto;
		bottom: 8%;
	}

	/* ---- What is in the box ----------------------------------------------- */
	.ticket__includes ul {
		display: flex;
		flex-direction: column;
		gap: 0.7rem;
		font-size: var(--fs-small);
		color: var(--text-2);
		list-style: none;
	}

	.ticket__includes li {
		display: flex;
		align-items: flex-start;
		gap: 0.6rem;
	}

	.ticket__includes :global(svg) {
		flex: none;
		margin-top: 0.18rem;
		color: var(--green);
	}

	.ticket__serial {
		margin-top: auto;
		padding-top: 0.9rem;
		border-top: 2px dashed var(--line);
		font-size: var(--fs-micro);
		letter-spacing: 0.1em;
		color: var(--text-3);
		opacity: 0.75;
	}

	/* ---- Layout ----------------------------------------------------------- */
	@media (min-width: 860px) {
		.ticket {
			grid-template-columns: minmax(0, 1.02fr) 0 1fr;
			align-items: stretch;
		}
	}

	/* Below the two-column break the ticket reads as one long receipt: the
	   perforation becomes a horizontal rule, punch marks and all. */
	@media (max-width: 859px) {
		.ticket__perf {
			border-left: 0;
			border-top: 2px dashed var(--line-strong);
		}

		.ticket__punch {
			top: 50%;
			left: 12%;
			translate: 0 -50%;
		}

		.ticket__punch--low {
			top: 50%;
			left: auto;
			right: 12%;
			bottom: auto;
		}
	}

	@media (max-width: 480px) {
		.ticket__cta > :global(.btn) {
			width: 100%;
		}
	}
</style>
