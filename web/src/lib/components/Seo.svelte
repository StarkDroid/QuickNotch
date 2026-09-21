<script lang="ts">
	import { platforms, site, stats } from '$data/site';

	/**
	 * All document-level SEO in one place.
	 *
	 * Every value is derived from the same content module the page renders, so
	 * the structured data can never drift from the visible copy — which is
	 * exactly what rich-result validators complain about.
	 */

	const title = 'QuickNotch — a Dynamic Island–style app launcher for Windows & macOS';
	const canonical = `${site.url}/`;
	const ogImage = `${site.url}/og.png`;

	const osList = platforms.map((platform) => platform.requirement).join(', ');

	const softwareSchema = {
		'@context': 'https://schema.org',
		'@type': 'SoftwareApplication',
		name: site.name,
		alternateName: 'QuickNotch floating dock',
		applicationCategory: 'UtilitiesApplication',
		applicationSubCategory: 'Application Launcher',
		operatingSystem: osList,
		softwareVersion: site.version,
		downloadUrl: site.downloadUrl,
		installUrl: site.downloadUrl,
		isAccessibleForFree: true,
		description: site.description,
		inLanguage: 'en',
		keywords: site.keywords.join(', '),
		offers: {
			'@type': 'Offer',
			price: site.price,
			priceCurrency: site.currency,
			availability: 'https://schema.org/InStock'
		},
		author: {
			'@type': 'Organization',
			name: site.author,
			url: site.url
		},
		featureList: [
			'Five glanceable widgets with vertical-scroll pagination',
			'Quick-launch apps, weather, system usage, game FPS and live activity controls',
			'Three notch styles: AMOLED notch, floating pill or drag handle',
			'Pin any installed app or browse for one on disk',
			'Launch from anywhere with always-on-top overlay',
			'Start at login automatically'
		],
		// Interesting, indexable facts rather than generic marketing filler.
		additionalProperty: stats.map((stat) => ({
			'@type': 'PropertyValue',
			name: stat.label,
			value: stat.value
		}))
	};

	const websiteSchema = {
		'@context': 'https://schema.org',
		'@type': 'WebSite',
		name: site.name,
		url: canonical,
		description: site.shortDescription,
		inLanguage: 'en'
	};

	const organizationSchema = {
		'@context': 'https://schema.org',
		'@type': 'Organization',
		name: site.author,
		url: site.url,
		description: `Makers of ${site.name}, a floating app launcher for Windows and macOS.`
	};

	/** Escapes `<` so a JSON string can never terminate the script tag early. */
	function serialise(value: unknown): string {
		return JSON.stringify(value).replace(/</g, '\\u003c');
	}
</script>

<svelte:head>
	<title>{title}</title>
	<meta name="description" content={site.description} />
	<link rel="canonical" href={canonical} />

	<meta name="robots" content="index, follow, max-image-preview:large, max-snippet:-1" />
	<meta name="googlebot" content="index, follow" />
	<meta name="author" content={site.author} />
	<meta name="keywords" content={site.keywords.join(', ')} />
	<meta name="application-name" content={site.name} />
	<meta name="generator" content="SvelteKit" />

	<meta property="og:type" content="website" />
	<meta property="og:site_name" content={site.name} />
	<meta property="og:title" content={title} />
	<meta property="og:description" content={site.description} />
	<meta property="og:url" content={canonical} />
	<meta property="og:locale" content="en_US" />
	<meta property="og:image" content={ogImage} />
	<meta property="og:image:width" content="1200" />
	<meta property="og:image:height" content="630" />
	<meta
		property="og:image:alt"
		content="QuickNotch: a floating notch on a desktop that expands into a dock of pinned apps."
	/>

	<meta name="twitter:card" content="summary_large_image" />
	<meta name="twitter:title" content={title} />
	<meta name="twitter:description" content={site.shortDescription} />
	<meta name="twitter:image" content={ogImage} />
	<meta
		name="twitter:image:alt"
		content="QuickNotch: a floating notch on a desktop that expands into a dock of pinned apps."
	/>

	{@html `<script type="application/ld+json">${serialise(softwareSchema)}<\/script>`}
	{@html `<script type="application/ld+json">${serialise(websiteSchema)}<\/script>`}
	{@html `<script type="application/ld+json">${serialise(organizationSchema)}<\/script>`}
</svelte:head>
