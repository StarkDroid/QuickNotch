<script lang="ts">
	import { stats } from '$data/site';
	import { stagger } from '$lib/actions/reveal';
</script>

<section class="section-tight" aria-label="QuickNotch at a glance">
	<div class="container">
		<ul class="stats" use:stagger={{ step: 0.09 }} data-stagger>
			{#each stats as stat (stat.label)}
				<li class="stat">
					<span class="stat__value">{stat.value}</span>
					<span class="stat__label">{stat.label}</span>
					<span class="stat__detail">{stat.detail}</span>
				</li>
			{/each}
		</ul>
	</div>
</section>

<style>
	.stats {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(min(100%, 13rem), 1fr));
		gap: 1px;
		border: 1px solid var(--line);
		border-radius: var(--r-lg);
		background: var(--line);
		overflow: hidden;
	}

	.stat {
		display: flex;
		flex-direction: column;
		gap: 0.3rem;
		padding: clamp(1.25rem, 2.4vw, 2rem);
		background: rgb(255 255 255 / 0.75);
	}

	.stat__value {
		font-family: var(--font-display);
		font-size: clamp(1.4rem, 2.9vw, 2.2rem);
		font-weight: 700;
		letter-spacing: -0.045em;
		line-height: 1;
		color: var(--accent-text);
	}

	.stat__label {
		font-family: var(--font-display);
		font-size: 0.98rem;
		font-weight: 600;
		color: var(--text);
	}

	.stat__detail {
		font-size: var(--fs-small);
		color: var(--text-3);
		line-height: 1.5;
	}
</style>
