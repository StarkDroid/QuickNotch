<script lang="ts">
	import Footer from './Footer.svelte';
	import Icon from './Icon.svelte';
	import Nav from './Nav.svelte';
	import { chrome, site } from '$data/site';

	interface Section {
		id: string;
		title: string;
		paragraphs: readonly string[];
	}

	interface Props {
		doc: {
			eyebrow: string;
			title: string;
			lead: string;
			updated: string;
			sections: readonly Section[];
		};
	}

	let { doc }: Props = $props();
</script>

<Nav />

<main id="main">
	<section class="section legal" id="top">
		<div class="container container-narrow">
			<header class="section-head">
				<a class="back" href={site.homePath} aria-label={site.homeLabel}>
					<Icon name="arrow-left" size={13} />
					{chrome.home}
				</a>
				<p class="eyebrow">{doc.eyebrow}</p>
				<h1 class="display h2">{doc.title}</h1>
				<p class="lead">{doc.lead}</p>
				<p class="legal__updated mono">{doc.updated}</p>
			</header>

			<div class="legal__body">
				{#each doc.sections as section (section.id)}
					<article class="legal__section" id={section.id}>
						<h2 class="display h3">{section.title}</h2>
						{#each section.paragraphs as paragraph}
							<p>
								{#each paragraph.split('`') as part, index}
									{#if index % 2 === 1}<code>{part}</code>{:else}{part}{/if}
								{/each}
							</p>
						{/each}
					</article>
				{/each}
			</div>
		</div>
	</section>
</main>

<Footer />

<style>
	.legal__updated {
		color: var(--text-3);
		font-size: var(--fs-small);
	}

	.legal__body {
		display: flex;
		flex-direction: column;
		gap: clamp(1.8rem, 4vw, 2.6rem);
		max-width: 68ch;
	}

	.legal__section {
		scroll-margin-top: calc(var(--header-h) + 1rem);
	}

	.legal__section h2 {
		margin-bottom: 0.6rem;
	}

	.legal__section p {
		color: var(--text-2);
		line-height: 1.72;
	}

	.legal__section p + p {
		margin-top: 0.75rem;
	}

	.legal__section code {
		padding: 0.08em 0.3em;
		border-radius: 3px;
		background: var(--paper-deep);
		color: var(--ink);
		font-family: var(--font-mono);
		font-size: 0.88em;
		overflow-wrap: anywhere;
	}
</style>
