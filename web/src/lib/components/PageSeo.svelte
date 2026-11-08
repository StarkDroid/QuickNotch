<script lang="ts">
	import { seo, site } from '$data/site';

	interface Props {
		/** Page title, with or without the site name. */
		title: string;
		description: string;
		/** Route, e.g. `/changelog`. */
		path: string;
		/** Turned off for the 404 page, which must never enter an index. */
		index?: boolean;
	}

	let { title, description, path, index = true }: Props = $props();

	const canonical = $derived(`${site.url}${path}`);
	const fullTitle = $derived(title.includes(site.name) ? title : `${title} · ${site.name}`);
	const image = `${site.url}/og.png`;
	const webPage = $derived({
		'@context': 'https://schema.org',
		'@type': 'WebPage',
		'@id': `${canonical}#webpage`,
		url: canonical,
		name: fullTitle,
		description,
		inLanguage: 'en',
		isPartOf: { '@type': 'WebSite', name: site.name, url: site.url },
		about: { '@type': 'SoftwareApplication', name: site.name, url: site.url },
		primaryImageOfPage: { '@type': 'ImageObject', url: image }
	});
	const breadcrumb = $derived({
		'@context': 'https://schema.org',
		'@type': 'BreadcrumbList',
		itemListElement: [
			{ '@type': 'ListItem', position: 1, name: 'Home', item: `${site.url}/` },
			{ '@type': 'ListItem', position: 2, name: title, item: canonical }
		]
	});

	/** Escapes `<` so a JSON string can never terminate the script tag early. */
	function serialise(value: unknown): string {
		return JSON.stringify(value).replace(/</g, '\\u003c');
	}
</script>

<svelte:head>
	<title>{fullTitle}</title>
	<meta name="description" content={description} />
	<link rel="canonical" href={canonical} />
	<meta
		name="robots"
		content={index ? 'index, follow, max-image-preview:large, max-snippet:-1' : 'noindex, follow'}
	/>
	<meta property="og:type" content="website" />
	<meta property="og:site_name" content={site.name} />
	<meta property="og:title" content={fullTitle} />
	<meta property="og:description" content={description} />
	<meta property="og:url" content={canonical} />
	<meta property="og:locale" content="en_US" />
	<meta property="og:image" content={image} />
	<meta property="og:image:width" content="1200" />
	<meta property="og:image:height" content="630" />
	<meta property="og:image:type" content="image/png" />
	<meta property="og:image:alt" content={seo.ogImageAlt} />
	<meta name="twitter:card" content="summary_large_image" />
	<meta name="twitter:title" content={fullTitle} />
	<meta name="twitter:description" content={description} />
	<meta name="twitter:image" content={image} />
	<meta name="twitter:image:alt" content={seo.ogImageAlt} />

	{@html `<script type="application/ld+json">${serialise(webPage)}<\/script>`}
	{#if index}
		{@html `<script type="application/ld+json">${serialise(breadcrumb)}<\/script>`}
	{/if}
</svelte:head>
