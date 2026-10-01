<script lang="ts">
	import Icon from './Icon.svelte';
	import { download, hero, platforms, site } from '$data/site';
	import { reveal, stagger } from '$lib/actions/reveal';
</script>

<section class="section" id="download">
	<div class="container">
		<div class="panel glass" use:reveal={{ from: 'up' }} data-reveal="up">
			<span class="panel__tape tape" aria-hidden="true"></span>
			<span class="panel__ticket" aria-hidden="true"></span>

			<div class="panel__inner">
				<span class="sticker panel__ribbon" aria-hidden="true">no card required ★</span>

				<p class="eyebrow">Get started</p>

				<h2 class="display h2">{download.title}</h2>

				<p class="lead">{download.body}</p>

				<div class="actions">
					<a
						class="btn btn--primary btn--lg"
						href={site.downloadUrl}
						target="_blank"
						rel="noopener noreferrer"
					>
						<Icon name="download" size={18} />
						{hero.primaryCta}
					</a>

					<div class="platforms">
						{#each platforms as platform (platform.id)}
							<a
								class="btn btn--ghost platform"
								href={site.downloadUrl}
								target="_blank"
								rel="noopener noreferrer"
							>
								<Icon name={platform.icon} size={20} />
								<span class="platform__text">
									<strong>{platform.name}</strong>
									<span>{platform.note}</span>
								</span>
							</a>
						{/each}
					</div>
				</div>

				<ul class="notes" use:stagger={{ step: 0.08 }} data-stagger>
					{#each download.notes as note (note)}
						<li><Icon name="check" size={16} /><span>{note}</span></li>
					{/each}
				</ul>

				<ul class="trust">
					<li><Icon name="shield" size={15} />No account · No telemetry</li>
					<li><Icon name="check" size={15} />v{site.version}</li>
				</ul>
			</div>
		</div>
	</div>
</section>

<style>
	.panel {
		position: relative;
		/* The glass surface is the big poster card now: paper sheet, ink keyline,
		   hard print shadow. No moving aurora — flat spot tints instead. */
		isolation: isolate;
		overflow: hidden;
		border-radius: var(--r-xl);
		border: 2px solid var(--ink);
		background-color: var(--paper-lift);
		box-shadow: var(--shadow-3);
		padding: clamp(1.5rem, 4.6vw, 3.5rem);
	}

	/* A two-toned paper background: flat accent corner + cream field still reads
	   as a print, and costs nothing to paint. */
	.panel::before {
		content: '';
		position: absolute;
		inset: -1px;
		z-index: -1;
		background: var(--accent-soft);
		border-radius: inherit;
	}

	/* Washi tape across the top, ticket stub across the bottom. */
	.panel__tape {
		top: -0.8rem;
		left: 50%;
		translate: -50% 0;
		rotate: -2deg;
	}

	.panel__ticket {
		position: absolute;
		left: 10%;
		right: 10%;
		bottom: -1px;
		border-top: 3px dashed var(--accent-line);
	}

	.panel__ribbon {
		position: absolute;
		top: -1.1rem;
		right: clamp(1rem, 4vw, 2.5rem);
	}

	.panel__inner {
		position: relative;
		display: flex;
		flex-direction: column;
		gap: clamp(1.4rem, 3vw, 2.1rem);
	}

	.actions {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: 0.75rem;
	}

	.platforms {
		display: flex;
		flex-wrap: wrap;
		gap: 0.6rem;
	}

	.platform {
		justify-content: flex-start;
		padding: 0.7rem 1rem;
		/* The shared .btn rule forbids wrapping; these labels are two lines tall. */
		white-space: normal;
		text-align: left;
	}

	.platform__text {
		display: flex;
		flex-direction: column;
		line-height: 1.3;
	}

	.platform__text strong {
		font-family: var(--font-display);
		font-size: 0.95rem;
		font-weight: 700;
		color: var(--text);
	}

	.platform__text span {
		font-family: var(--font-body);
		font-weight: 400;
		font-size: var(--fs-micro);
		letter-spacing: 0;
		color: var(--text-3);
	}

	.notes {
		display: flex;
		flex-direction: column;
		gap: 0.7rem;
		font-size: var(--fs-small);
		color: var(--text-2);
	}

	.notes li {
		display: flex;
		align-items: flex-start;
		gap: 0.6rem;
	}

	.notes :global(svg) {
		color: var(--green);
		flex: none;
		margin-top: 0.18rem;
	}

	.trust {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: 0.55rem 1.5rem;
		margin-top: 0.25rem;
		font-size: var(--fs-small);
		color: var(--text-2);
	}

	.trust li {
		display: inline-flex;
		align-items: center;
		gap: 0.42rem;
	}

	.trust :global(svg) {
		color: var(--green);
		flex: none;
	}

	@media (max-width: 620px) {
		.actions > .btn--primary,
		.platforms,
		.platform {
			width: 100%;
		}

		.platforms {
			flex-direction: column;
		}
	}
</style>
