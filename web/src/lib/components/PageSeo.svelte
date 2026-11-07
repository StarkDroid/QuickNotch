<script lang="ts">
	import { site } from '$data/site';

	interface Props {
		/** Page title, without the site name. */
		title: string;
		description: string;
		/** Route, e.g. `/changelog`. */
		path: string;
		/** Turned off for the 404 page, which must never enter an index. */
		index?: boolean;
	}

	let { title, description, path, index = true }: Props = $props();

	const canonical = $derived(`${site.url}${path}`);
	const fullTitle = $derived(`${title} · ${site.name}`);
</script>

<svelte:head>
	<title>{fullTitle}</title>
	<meta name="description" content={description} />
	<link rel="canonical" href={canonical} />
	<meta name="robots" content={index ? 'index, follow, max-image-preview:large' : 'noindex, follow'} />
	<meta property="og:type" content="website" />
	<meta property="og:site_name" content={site.name} />
	<meta property="og:title" content={fullTitle} />
	<meta property="og:description" content={description} />
	<meta property="og:url" content={canonical} />
	<meta property="og:image" content={`${site.url}/og.png`} />
	<meta name="twitter:card" content="summary_large_image" />
	<meta name="twitter:title" content={fullTitle} />
	<meta name="twitter:description" content={description} />
	<meta name="twitter:image" content={`${site.url}/og.png`} />
</svelte:head>
