<script lang="ts">
	import Footer from '$components/Footer.svelte';
	import Icon from '$components/Icon.svelte';
	import Nav from '$components/Nav.svelte';
	import PageSeo from '$components/PageSeo.svelte';
	import { site } from '$data/site';
	import { changelog, releasePage, type ChangelogRelease } from '$data/releases';

	/**
	 * The published history, newest first.
	 *
	 * The feed is the same `changelog.json` the release workflow attaches to the
	 * release and the desktop app reads under Settings → Changelog — committed to
	 * the site and imported at build time, so this page is real prerendered text
	 * rather than something that appears after hydration.
	 */
	const kinds = [
		{ id: 'new', label: 'New' },
		{ id: 'improved', label: 'Improved' },
		{ id: 'fixed', label: 'Fixed' }
	] as const;

	function groups(release: ChangelogRelease) {
		const source =
			release.changes.length > 0
				? release.changes
				: release.highlights.map((text) => ({ kind: 'new', text }));
		return kinds
			.map((kind) => ({
				...kind,
				items: source.filter((change) => change.kind === kind.id).map((change) => change.text)
			}))
			.filter((group) => group.items.length > 0);
	}

	function formatDate(value: string) {
		const date = new Date(value);
		if (Number.isNaN(date.getTime())) return value;
		return date.toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' });
	}
</script>

<PageSeo
	title="Changelog"
	description="Every QuickNotch release, newest first: new widgets, fixes and improvements for the Windows and macOS notch, straight from the published release feed."
	path={site.changelogPath}
/>

<Nav />

<main id="main">
	<section class="section changelog" id="top">
		<div class="container container-narrow">
			<header class="section-head">
				<a class="back" href={site.homePath}>
					<Icon name="arrow-left" size={13} />
					QuickNotch
				</a>
				<p class="eyebrow">The changelog</p>
				<h1 class="display h2">Every release, in public.</h1>
				<p class="lead">
					The same feed the app shows under Settings → Changelog. Newest first, written for
					people rather than for a diff.
				</p>
			</header>

			{#if changelog.releases.length === 0}
				<div class="changelog__state">
					<p>The first public release is still being published.</p>
					<a
						class="btn btn--ghost btn--sm"
						href={site.releasesUrl}
						target="_blank"
						rel="noopener noreferrer"
					>
						<Icon name="github" size={16} />
						Read it on GitHub
					</a>
				</div>
			{:else}
				{#if changelog.latest}
					<p class="changelog__current mono">
						Latest release <strong class="version">v{changelog.latest}</strong>
						{#if changelog.updatedAt}
							<span aria-hidden="true">·</span>
							<span>updated {formatDate(changelog.updatedAt)}</span>
						{/if}
					</p>
				{/if}

				<ol class="releases">
					{#each changelog.releases as release (release.version)}
						<li class="release">
							<header class="release__head">
								<span class="chip chip--accent mono chip--version">v{release.version}</span>
								<span class="mono release__date">{formatDate(release.date)}</span>
								<a
									class="mono release__link"
									href={releasePage(release.version)}
									target="_blank"
									rel="noopener noreferrer"
								>
									release page
									<Icon name="arrow-up-right" size={13} />
								</a>
							</header>

							<h2 class="release__title">{release.title}</h2>

							<div class="release__groups">
								{#each groups(release) as group (group.id)}
									<div class="group">
										<p class="mono group__label" data-kind={group.id}>{group.label}</p>
										<ul class="group__items">
											{#each group.items as item (item)}
												<li>{item}</li>
											{/each}
										</ul>
									</div>
								{/each}
							</div>
						</li>
					{/each}
				</ol>
			{/if}

			<p class="changelog__source mono">
				Source: <a href={site.repository} target="_blank" rel="noopener noreferrer">
					github.com/StarkDroid/QuickNotch
				</a> · {site.license} licensed
			</p>
		</div>
	</section>
</main>

<Footer />

<style>
	.changelog {
		padding-top: clamp(2rem, 5vw, 3.5rem);
	}

	.changelog .section-head {
		margin-bottom: clamp(1.5rem, 3vw, 2.25rem);
	}

	.changelog__current {
		display: flex;
		flex-wrap: wrap;
		gap: 0.4rem;
		align-items: baseline;
		margin-bottom: 1.25rem;
		font-size: var(--fs-micro);
		letter-spacing: 0.06em;
		color: var(--text-3);
	}

	.changelog__current strong {
		color: var(--accent-text);
	}

	.changelog__state {
		display: flex;
		flex-direction: column;
		align-items: flex-start;
		gap: 1rem;
		padding: 1.5rem;
		border: 1.5px dashed var(--line-strong);
		border-radius: var(--r-md);
		background: var(--paper-deep);
		color: var(--text-2);
	}

	/* ---- Releases --------------------------------------------------------- */
	.releases {
		display: flex;
		flex-direction: column;
		gap: 0;
		list-style: none;
	}

	.release {
		padding-block: clamp(1.5rem, 3vw, 2rem);
		border-top: 2px dashed var(--line);
	}

	.release:last-child {
		border-bottom: 2px dashed var(--line);
	}

	.release__head {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: 0.6rem 0.85rem;
	}

	.release__date {
		font-size: var(--fs-micro);
		letter-spacing: 0.06em;
		color: var(--text-3);
	}

	.release__link {
		display: inline-flex;
		align-items: center;
		gap: 0.25rem;
		margin-left: auto;
		font-size: var(--fs-micro);
		letter-spacing: 0.06em;
		color: var(--text-3);
		text-decoration: underline;
		text-decoration-color: var(--line-strong);
		text-underline-offset: 3px;
	}

	.release__link:hover {
		color: var(--accent-text);
		text-decoration-color: var(--accent);
	}

	.release__title {
		margin-top: 0.65rem;
		font-family: var(--font-display);
		font-size: clamp(1.25rem, 2.4vw, 1.55rem);
		font-weight: 700;
		letter-spacing: -0.02em;
	}

	.release__groups {
		display: flex;
		flex-direction: column;
		gap: 0.9rem;
		margin-top: 1rem;
	}

	.group {
		display: flex;
		flex-direction: column;
		gap: 0.4rem;
	}

	.group__label {
		display: inline-flex;
		align-items: center;
		gap: 0.45rem;
		font-size: var(--fs-micro);
		letter-spacing: 0.16em;
		text-transform: uppercase;
		color: var(--text-3);
	}

	/* Flat colour dots, one per kind — the ticket's spot colours, no gradients. */
	.group__label::before {
		content: '';
		width: 7px;
		height: 7px;
		border-radius: 50%;
		background: var(--blue);
	}

	.group__label[data-kind='improved']::before {
		background: var(--accent);
	}

	.group__label[data-kind='fixed']::before {
		background: var(--green);
	}

	.group__items {
		display: flex;
		flex-direction: column;
		gap: 0.35rem;
		padding-left: 1.15rem;
		font-size: var(--fs-small);
		color: var(--text-2);
		list-style: disc;
	}

	.group__items li::marker {
		color: var(--line-strong);
	}

	.changelog__source {
		margin-top: clamp(1.75rem, 4vw, 2.5rem);
		font-size: var(--fs-micro);
		letter-spacing: 0.06em;
		color: var(--text-3);
		text-align: center;
	}

	.changelog__source a {
		text-decoration: underline;
		text-decoration-color: var(--line-strong);
		text-underline-offset: 3px;
	}

	.changelog__source a:hover {
		color: var(--accent-text);
		text-decoration-color: var(--accent);
	}
</style>
