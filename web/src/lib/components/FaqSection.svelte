<script lang="ts">
	import { faq, faqHead } from '$data/site';
	import { reveal, stagger } from '$lib/actions/reveal';

	/**
	 * Native details/summary, so disclosure works without JS. Answers are
	 * open in the prerendered HTML, then collapse after hydration.
	 */
	let hydrated = $state(false);
	let openIndex = $state(0);

	$effect(() => {
		// $effect never runs during SSR, so this flag flips only after hydration.
		if (typeof window === 'undefined') return;
		hydrated = true;
		openIndex = 0;
	});

	function onTrigger(index: number, event: MouseEvent) {
		// Without JS the native <details> toggle is left to the browser; with JS
		// we own the open state so only one panel is ever expanded.
		if (!hydrated) return;
		event.preventDefault();
		openIndex = openIndex === index ? -1 : index;
	}
</script>

<section class="section faq" id="faq" aria-labelledby="faq-heading">
	<div class="container container-narrow">
		<header class="section-head section-head--center" use:reveal={{ from: 'up' }} data-reveal="up">
			<h2 class="display h2" id="faq-heading">{faqHead.title}</h2>
			<p class="lead">{faqHead.lead}</p>
		</header>

		<div class="faq__list" use:stagger={{ step: 0.06 }} data-stagger>
			{#each faq as item, index (item.question)}
				<details
					class="faq__item"
					class:open={openIndex === index}
					open={!hydrated || openIndex === index}
				>
					<summary class="faq__trigger" onclick={(event) => onTrigger(index, event)}>
						<span class="faq__q">{item.question}</span>
					</summary>
					<div class="faq__panel">
						<p>{item.answer}</p>
					</div>
				</details>
			{/each}
		</div>
	</div>
</section>

<style>
	.faq__list {
		display: flex;
		flex-direction: column;
		gap: 0.85rem;
	}

	.faq__item {
		border: 2px solid var(--ink);
		border-radius: var(--r-md);
		background: var(--paper-lift);
		box-shadow: 3px 3px 0 rgb(33 28 19 / 0.85);
		overflow: hidden;
		transition: border-color var(--dur) var(--ease-out), background-color var(--dur) var(--ease-out),
			transform var(--dur-fast) var(--ease-out), box-shadow var(--dur) var(--ease-out);
	}

	.faq__item:hover,
	.faq__item.open {
		border-color: var(--ink);
	}

	.faq__item:hover {
		transform: translate(-1px, -1px);
		box-shadow: 4px 4px 0 rgb(33 28 19 / 0.85);
	}

	/* Left accent rail lights up on the open panel — a flat indicator, not a glow. */
	.faq__item.open {
		box-shadow: inset 5px 0 0 var(--accent);
		background: var(--paper-lift);
	}

	.faq__trigger {
		display: flex;
		align-items: center;
		gap: 1rem;
		width: 100%;
		padding: 1.05rem 1.25rem;
		text-align: left;
		cursor: pointer;
		list-style: none;
		color: inherit;
	}

	.faq__trigger::-webkit-details-marker {
		display: none;
	}

	.faq__trigger::after {
		content: '+';
		flex: none;
		display: grid;
		place-items: center;
		width: 30px;
		height: 30px;
		border-radius: var(--r-sm);
		border: 2px solid var(--ink);
		background: var(--paper-deep);
		font-size: 1.1rem;
		line-height: 1;
		color: var(--text-2);
		box-shadow: 2px 2px 0 rgb(33 28 19 / 0.75);
		transition: transform var(--dur) var(--ease-out), color var(--dur) var(--ease-out),
			background-color var(--dur) var(--ease-out);
	}

	.faq__item.open .faq__trigger::after {
		transform: rotate(45deg);
		color: var(--on-accent);
		background: var(--accent);
		border-color: var(--accent);
		box-shadow: none;
	}

	.faq__q {
		font-family: var(--font-display);
		font-weight: 700;
		font-size: 1.05rem;
		letter-spacing: -0.015em;
		color: var(--text);
	}

	.faq__panel {
		padding: 0 1.25rem 1.15rem;
		color: var(--text-2);
	}

	.faq__panel p {
		max-width: 62ch;
		line-height: 1.62;
	}

	/* It is the last section on the page: keep the footer close. */
	.faq {
		padding-bottom: clamp(1.5rem, 3vw, 2.5rem);
	}
</style>
