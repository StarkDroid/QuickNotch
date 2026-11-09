<script lang="ts">
	import { onMount } from 'svelte';
	import Footer from '$components/Footer.svelte';
	import Icon from '$components/Icon.svelte';
	import KofiButton from '$components/KofiButton.svelte';
	import Nav from '$components/Nav.svelte';
	import PageSeo from '$components/PageSeo.svelte';
	import { chrome, download, platforms, site, sourceNote } from '$data/site';
	import {
		installersFor,
		latest,
		platformOf,
		releaseAsset,
		releasePage
	} from '$data/releases';

	/**
	 * Installer URLs come from the newest published release. Starting a
	 * download opens a native dialog; the link itself is never intercepted.
	 */
	let visitor = $state<'windows' | 'macos' | null>(null);
	let thanks = $state<HTMLDialogElement | null>(null);

	onMount(() => {
		visitor = platformOf(navigator.userAgent);
	});

	function thank() {
		thanks?.showModal();
	}

	/** A click on the backdrop dismisses; inside the card it never does. */
	function closeFromBackdrop(event: MouseEvent) {
		if (event.target === thanks) thanks?.close();
	}
</script>

<PageSeo
	title={download.seo.title}
	description={download.seo.description}
	path={site.downloadPath}
/>

<Nav />

<main id="main">
	<section class="section download" id="top">
		<div class="container">
			<header class="section-head download__head">
				<a class="back" href={site.homePath} aria-label={site.homeLabel}>
					<Icon name="arrow-left" size={13} />
					{chrome.home}
				</a>
				<p class="eyebrow">{download.eyebrow}</p>
				<h1 class="display h2">{download.title}</h1>
				<p class="lead">{download.lead}</p>

				<p class="download__meta mono">
					<span class="chip chip--accent chip--version">
						{download.versionLabel} <span class="version">v{latest}</span>
					</span>
					<a href={releasePage(latest)} target="_blank" rel="noopener noreferrer">
						{download.releaseNotes}
					</a>
					<a href={site.changelogPath}>{download.changelogLink}</a>
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
										<span class="platform__picked">· {download.yourPlatform}</span>
									{/if}
								</p>
							</div>
						</header>

						<p class="platform__blurb">{platform.blurb}</p>

						<div class="platform__actions">
							{#each installersFor(platform.id) as installer (installer.id)}
								<a
									class="btn btn--primary"
									href={releaseAsset(latest, installer.file(latest))}
									onclick={thank}
								>
									<Icon name="download" size={17} />
									<span class="platform__label">
										{installer.label}
										<span class="platform__detail mono">{installer.detail}</span>
									</span>
								</a>
							{/each}
						</div>
					</article>
				{/each}
			</div>

			<section class="first-launch" aria-labelledby="first-launch-title">
				<header class="first-launch__head">
					<p class="eyebrow">{download.firstLaunch.eyebrow}</p>
					<h2 class="display h3" id="first-launch-title">{download.firstLaunch.title}</h2>
					<p class="first-launch__lead">{download.firstLaunch.lead}</p>
				</header>

				<div class="first-launch__grid">
					<article class="first-launch__card">
						<h3 class="first-launch__platform">{download.firstLaunch.macos.title}</h3>
						<ol>
							{#each download.firstLaunch.macos.steps as step}
								<li>{step}</li>
							{/each}
						</ol>
						<div class="first-launch__terminal">
							<span class="mono first-launch__terminal-label">
								{download.firstLaunch.macos.terminalLabel}
							</span>
							<code>{download.firstLaunch.macos.terminal}</code>
						</div>
					</article>

					<article class="first-launch__card">
						<h3 class="first-launch__platform">{download.firstLaunch.windows.title}</h3>
						<ol>
							{#each download.firstLaunch.windows.steps as step}
								<li>{step}</li>
							{/each}
						</ol>
					</article>
				</div>

				<p class="first-launch__note">{download.firstLaunch.note}</p>
			</section>

			<div class="notes">
				<p class="notes__item notes__item--quiet mono">
					<Icon name="layers" size={15} />
					<span>{download.checksums}</span>
				</p>
			</div>

			<p class="download__source mono">
				{sourceNote.label}:
				<a href={site.repository} target="_blank" rel="noopener noreferrer">
					{site.repositoryLabel}
				</a> · {sourceNote.licensed}
			</p>
		</div>
	</section>
</main>

<dialog
	class="thanks"
	bind:this={thanks}
	aria-labelledby="thanks-title"
	aria-describedby="thanks-body"
	onclick={closeFromBackdrop}
>
	<div class="thanks__card">
		<p class="eyebrow">{download.thanks.eyebrow}</p>
		<h2 class="thanks__title" id="thanks-title">{download.thanks.title}</h2>
		<p class="thanks__body" id="thanks-body">{download.thanks.body}</p>
		<div class="thanks__actions">
			<KofiButton />
			<button class="btn btn--ghost btn--sm" type="button" onclick={() => thanks?.close()}>
				{download.thanks.dismiss}
			</button>
		</div>
	</div>
</dialog>

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

	.first-launch {
		margin-top: clamp(2rem, 4vw, 3rem);
		padding: clamp(1.4rem, 3vw, 2rem);
		border: 2px solid var(--ink);
		border-radius: var(--r-lg);
		background: var(--paper-lift);
		box-shadow: var(--shadow-3);
	}

	.first-launch__head {
		display: flex;
		flex-direction: column;
		gap: 0.7rem;
		max-width: 68ch;
		margin-bottom: 1.5rem;
	}

	.first-launch__lead {
		color: var(--text-2);
		line-height: 1.65;
	}

	.first-launch__grid {
		display: grid;
		gap: 1.5rem;
	}

	@media (min-width: 760px) {
		.first-launch__grid {
			grid-template-columns: repeat(2, minmax(0, 1fr));
		}
	}

	.first-launch__card {
		min-width: 0;
	}

	.first-launch__platform {
		margin-bottom: 0.65rem;
		font-family: var(--font-display);
		font-size: 1.2rem;
	}

	.first-launch__card ol {
		display: flex;
		flex-direction: column;
		gap: 0.55rem;
		margin: 0;
		padding-left: 1.15rem;
		color: var(--text-2);
		line-height: 1.6;
	}

	.first-launch__terminal {
		display: flex;
		flex-direction: column;
		gap: 0.35rem;
		margin-top: 0.9rem;
	}

	.first-launch__terminal-label {
		color: var(--text-3);
		font-size: var(--fs-micro);
		letter-spacing: 0.06em;
		text-transform: uppercase;
	}

	.first-launch__terminal code {
		padding: 0.55rem 0.65rem;
		border-radius: var(--r-sm);
		background: var(--paper-deep);
		font-family: var(--font-mono);
		font-size: var(--fs-small);
		overflow-wrap: anywhere;
	}

	.first-launch__note {
		margin-top: 1.25rem;
		color: var(--text-3);
		font-size: var(--fs-small);
		line-height: 1.6;
	}

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

	/* A poster card on a dimmed page, offset with the same hard sticker shadow
	   as the platform cards. */
	.thanks {
		width: min(92vw, 26rem);
		max-width: none;
		max-height: calc(100vh - 2rem);
		margin: auto;
		padding: 0;
		border: 2px solid var(--ink);
		border-radius: var(--r-lg);
		background: var(--paper-lift);
		color: var(--text);
		box-shadow: var(--shadow-3);
	}

	.thanks::backdrop {
		background: rgb(33 28 19 / 0.5);
	}

	.thanks__card {
		display: flex;
		flex-direction: column;
		align-items: flex-start;
		gap: 0.7rem;
		padding: clamp(1.4rem, 4vw, 1.9rem);
	}

	.thanks__title {
		margin-top: 0.15rem;
		font-family: var(--font-display);
		font-size: clamp(1.4rem, 3vw, 1.75rem);
		font-weight: 700;
		line-height: 1.12;
		letter-spacing: -0.02em;
	}

	.thanks__body {
		font-size: var(--fs-small);
		color: var(--text-2);
	}

	.thanks__actions {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: 0.85rem;
		margin-top: 0.55rem;
	}

	@media (prefers-reduced-motion: no-preference) {
		.thanks[open] {
			animation: thanks-in var(--dur) var(--ease-out) both;
		}

		.thanks[open]::backdrop {
			animation: thanks-fade var(--dur-fast) var(--ease-out) both;
		}
	}

	@keyframes thanks-in {
		from {
			opacity: 0;
			transform: translateY(10px) scale(0.97);
		}
	}

	@keyframes thanks-fade {
		from {
			opacity: 0;
		}
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
