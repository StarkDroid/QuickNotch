<script lang="ts">
	import { onMount } from 'svelte';
	import { fly } from 'svelte/transition';
	import Footer from '$components/Footer.svelte';
	import Icon from '$components/Icon.svelte';
	import Nav from '$components/Nav.svelte';
	import PageSeo from '$components/PageSeo.svelte';
	import { download, platforms, site } from '$data/site';
	import {
		installersFor,
		latest,
		platformOf,
		releaseAsset,
		releasePage
	} from '$data/releases';

	/**
	 * The one page that hands over an installer.
	 *
	 * The version comes from the changelog feed the release workflow commits, so
	 * the asset URLs are built from the newest published release and never need a
	 * site change. Picking a download reveals the tip jar once — the only place
	 * the site asks for anything, and it asks after the file is already on its
	 * way.
	 */
	let visitor = $state<'windows' | 'macos' | null>(null);
	let picked = $state(false);

	onMount(() => {
		visitor = platformOf(navigator.userAgent);
	});
</script>

<PageSeo
	title="Download QuickNotch"
	description="Download QuickNotch for Windows 10 & 11 or macOS 12+, free and open source. One installer per platform, self-updating, MIT licensed."
	path={site.downloadPath}
/>

<Nav />

<main id="main">
	<section class="section download" id="top">
		<div class="container">
			<header class="section-head download__head">
				<p class="eyebrow">{download.eyebrow}</p>
				<h1 class="display h2">{download.title}</h1>
				<p class="lead">{download.lead}</p>

				<p class="download__meta mono">
					<span class="chip chip--accent chip--version">
						{download.versionLabel} <span class="version">v{latest}</span>
					</span>
					<a href={releasePage(latest)} target="_blank" rel="noopener noreferrer">release notes</a>
					<a href={site.changelogPath}>changelog</a>
					<a href={site.releasesUrl} target="_blank" rel="noopener noreferrer">
						{download.allReleases}
					</a>
				</p>
			</header>

			<div class="platforms">
				{#each platforms as platform (platform.id)}
					<article class="platform" class:platform--picked={visitor === platform.id}>
						<header class="platform__head">
							<span class="platform__mark"><Icon name={platform.icon} size={22} /></span>
							<div class="platform__id">
								<h2 class="platform__name">{platform.name}</h2>
								<p class="mono platform__requirement">
									{platform.requirement}
									{#if visitor === platform.id}
										<span class="platform__picked">· your platform</span>
									{/if}
								</p>
							</div>
						</header>

						<p class="platform__blurb">{platform.blurb}</p>

						<div class="platform__actions">
							{#each installersFor(platform.id) as installer (installer.id)}
								{#if installer.primary}
									<a
										class="btn btn--primary"
										href={releaseAsset(latest, installer.file(latest))}
										onclick={() => (picked = true)}
									>
										<Icon name="download" size={17} />
										<span class="platform__label">
											{installer.label}
											<span class="platform__detail mono">{installer.detail}</span>
										</span>
									</a>
								{:else}
									<a
										class="platform__alt"
										href={releaseAsset(latest, installer.file(latest))}
										onclick={() => (picked = true)}
									>
										{installer.label}
										<span class="platform__alt-detail">· {installer.detail}</span>
									</a>
								{/if}
							{/each}
						</div>

						<p class="mono platform__note">{platform.note}</p>
					</article>
				{/each}
			</div>

			<div class="notes">
				<p class="notes__item">
					<Icon name="shield" size={17} />
					<span>{download.unsigned}</span>
				</p>
				<p class="notes__item notes__item--quiet mono">
					<Icon name="layers" size={15} />
					<span>Every file is published as a GitHub release, with SHA256SUMS.txt beside it.</span>
				</p>
			</div>

			{#if picked}
				<aside class="support" transition:fly={{ y: 12, duration: 240 }} aria-live="polite">
					<div class="support__copy">
						<p class="eyebrow">{download.support.eyebrow}</p>
						<h2 class="support__title">{download.support.title}</h2>
						<p class="support__body">{download.support.body}</p>
					</div>
					<div class="support__actions">
						<a
							class="btn btn--primary"
							href={site.kofi}
							target="_blank"
							rel="noopener noreferrer"
						>
							<Icon name="coffee" size={17} />
							{download.support.cta}
						</a>
						<button class="btn btn--ghost btn--sm" type="button" onclick={() => (picked = false)}>
							{download.support.dismiss}
						</button>
					</div>
				</aside>
			{/if}

			<p class="download__source mono">
				Source: <a href={site.repository} target="_blank" rel="noopener noreferrer">
					github.com/StarkDroid/QuickNotch
				</a> · {site.license} licensed
			</p>
		</div>
	</section>
</main>

<Footer />

<style>
	.download {
		padding-top: clamp(2rem, 5vw, 3.5rem);
	}

	.download__head {
		margin-bottom: clamp(1.75rem, 4vw, 2.75rem);
	}

	.download__meta {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: 0.55rem 1.1rem;
		margin-top: 1.1rem;
		font-size: var(--fs-micro);
		letter-spacing: 0.06em;
		color: var(--text-3);
	}

	.download__meta a {
		text-decoration: underline;
		text-decoration-color: var(--line-strong);
		text-underline-offset: 3px;
	}


	.download__meta a:hover {
		color: var(--accent-text);
		text-decoration-color: var(--accent);
	}

	/* ---- Platform cards --------------------------------------------------- */
	.platforms {
		display: grid;
		gap: 1.25rem;
	}

	@media (min-width: 760px) {
		.platforms {
			grid-template-columns: repeat(2, minmax(0, 1fr));
		}
	}

	.platform {
		display: flex;
		flex-direction: column;
		min-width: 0;
		gap: 1rem;
		padding: clamp(1.4rem, 3vw, 1.9rem);
		border: 2px solid var(--ink);
		border-radius: var(--r-lg);
		background: var(--paper-lift);
		box-shadow: var(--shadow-3);
	}

	/* The matching card keeps the poster's flat accent ring rather than a glow. */
	.platform--picked {
		box-shadow:
			0 0 0 4px var(--accent-soft),
			var(--shadow-3);
	}

	.platform__head {
		display: flex;
		align-items: center;
		gap: 0.85rem;
	}

	.platform__mark {
		display: grid;
		place-items: center;
		width: 44px;
		height: 44px;
		flex: none;
		border-radius: var(--r-sm);
		border: 2px solid var(--ink);
		background: var(--paper);
	}

	.platform__id {
		min-width: 0;
	}

	.platform__name {
		font-family: var(--font-display);
		font-size: 1.35rem;
		font-weight: 700;
		letter-spacing: -0.02em;
	}

	.platform__requirement {
		font-size: var(--fs-micro);
		letter-spacing: 0.06em;
		color: var(--text-3);
	}

	/* The detected machine, inline in the requirement line so it never steals
	   width from the platform name on a phone. */
	.platform__picked {
		color: var(--accent-text);
	}

	.platform__blurb {
		font-size: var(--fs-small);
		color: var(--text-2);
	}

	.platform__actions {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: 0.7rem;
		margin-top: auto;
	}

	/* Same button in both cards, so a platform never looks like the default. */
	.platform__actions :global(.btn) {
		flex: 1 1 15rem;
		min-width: 0;
	}

	/* On a phone each primary button takes the full card width, so a long label
	   never has to fit beside an icon in a narrow half-column. */
	@media (max-width: 560px) {
		.platform__actions :global(.btn) {
			flex: 1 1 100%;
			white-space: normal;
		}
	}

	/* Label over file type: the two-line stack fits two buttons side by side on
	   a phone without either label running into its icon. */
	.platform__label {
		display: flex;
		flex-direction: column;
		align-items: flex-start;
		gap: 0.1rem;
		line-height: 1.15;
		text-align: left;
	}

	.platform__detail {
		font-size: 0.62rem;
		letter-spacing: 0.08em;
		opacity: 0.75;
	}

	.platform__alt {
		font-size: var(--fs-small);
		color: var(--text-3);
		text-decoration: underline;
		text-decoration-color: var(--line-strong);
		text-underline-offset: 3px;
	}

	.platform__alt-detail {
		margin-left: 0.35rem;
		color: var(--text-3);
		opacity: 0.8;
	}

	.platform__alt:hover {
		color: var(--accent-text);
		text-decoration-color: var(--accent);
	}

	.platform__note {
		font-size: var(--fs-micro);
		letter-spacing: 0.06em;
		color: var(--text-3);
	}

	/* ---- Install notes ---------------------------------------------------- */
	.notes {
		display: flex;
		flex-direction: column;
		gap: 0.75rem;
		margin-top: 1.5rem;
		padding: 1.1rem 1.25rem;
		border: 1.5px dashed var(--line-strong);
		border-radius: var(--r-md);
		background: var(--paper-deep);
	}

	.notes__item {
		display: flex;
		align-items: flex-start;
		gap: 0.65rem;
		font-size: var(--fs-small);
		color: var(--text-2);
	}

	.notes__item :global(svg) {
		flex: none;
		margin-top: 0.15rem;
		color: var(--text-3);
	}

	.notes__item--quiet {
		font-size: var(--fs-micro);
		letter-spacing: 0.05em;
		color: var(--text-3);
	}

	/* ---- Optional tip jar ------------------------------------------------- */
	.support {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: 1.25rem 2rem;
		margin-top: 1.5rem;
		padding: clamp(1.4rem, 3vw, 1.9rem);
		border: 2px solid var(--ink);
		border-radius: var(--r-lg);
		background: var(--paper-lift);
		box-shadow: var(--shadow-2);
	}

	.support__copy {
		flex: 1 1 24rem;
	}

	.support__title {
		margin-top: 0.35rem;
		font-family: var(--font-display);
		font-size: clamp(1.3rem, 2.6vw, 1.65rem);
		font-weight: 700;
		letter-spacing: -0.02em;
	}

	.support__body {
		margin-top: 0.45rem;
		font-size: var(--fs-small);
		color: var(--text-2);
	}

	.support__actions {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: 0.7rem;
	}

	.download__source {
		margin-top: clamp(2rem, 4vw, 3rem);
		font-size: var(--fs-micro);
		letter-spacing: 0.06em;
		color: var(--text-3);
		text-align: center;
	}

	.download__source a {
		text-decoration: underline;
		text-decoration-color: var(--line-strong);
		text-underline-offset: 3px;
	}

	.download__source a:hover {
		color: var(--accent-text);
		text-decoration-color: var(--accent);
	}
</style>
