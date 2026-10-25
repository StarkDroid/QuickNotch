<script lang="ts">
	import { faq, platforms, pricing, site, stats, widgets, themes } from '$data/site';

	/**
	 * All document-level SEO in one place.
	 *
	 * Every value is derived from the same content module the page renders, so
	 * the structured data can never drift from the visible copy — which is
	 * exactly what rich-result validators complain about.
	 */

	const title = 'QuickNotch: the desktop notch, app launcher and widgets for Windows and macOS';
	const canonical = `${site.url}/`;
	const ogImage = `${site.url}/og.png`;
	const logo = `${site.url}/icon-512.png`;

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
		fileFormat: 'application/octet-stream',
		sameAs: [canonical],
		isAccessibleForFree: false,
		description: site.description,
		inLanguage: 'en',
		keywords: site.keywords.join(', '),
		offers: {
			'@type': 'Offer',
			price: pricing.amount,
			priceCurrency: pricing.currency,
			availability: 'https://schema.org/InStock',
			url: site.purchaseUrl
		},
		author: {
			'@type': 'Organization',
			name: site.author,
			url: site.url,
			logo: { '@type': 'ImageObject', url: logo }
		},
		publisher: {
			'@type': 'Organization',
			name: site.author,
			url: site.url,
			logo: { '@type': 'ImageObject', url: logo }
		},
		featureList: [
			'Five glanceable widgets with vertical-scroll pagination',
			'Quick-launch apps, live system rings, weather, calendar and now-playing controls',
			'System page: CPU, GPU, memory, storage, download and upload rings',
			'Gaming HUD: FPS, CPU, GPU, latency and download rate while a fullscreen game runs',
			'Transparent in games: the notch body drops away, leaving a thin rim and soft shadow',
			'Three notch styles: AMOLED notch, floating pill or drag handle',
			'Solid ink or frosted glass, with the desktop blurring through the island',
			'Pin any installed app or browse for one on disk',
			'Launch from anywhere with always-on-top overlay',
			'Start at login automatically',
			'Runs offline with no account and no telemetry',
			'One licence covers Windows and macOS'
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
		inLanguage: 'en',
		publisher: {
			'@type': 'Organization',
			name: site.author,
			url: site.url,
			logo: { '@type': 'ImageObject', url: logo }
		}
	};

	const organizationSchema = {
		'@context': 'https://schema.org',
		'@type': 'Organization',
		name: site.author,
		url: site.url,
		logo: { '@type': 'ImageObject', url: logo },
		description: `Makers of ${site.name}, a floating app launcher and notch for Windows and macOS.`
	};

	/**
	 * FAQPage: the visible accordion below the fold is mirrored here, sourced
	 * from the same `faq` array so the rich result can never disagree with the
	 * rendered answers.
	 */
	const faqSchema = {
		'@context': 'https://schema.org',
		'@type': 'FAQPage',
		mainEntity: faq.map((item) => ({
			'@type': 'Question',
			name: item.question,
			acceptedAnswer: { '@type': 'Answer', text: item.answer }
		}))
	};

	/** ItemList of the widget stack, for entity-rich crawlable signals. */
	const widgetListSchema = {
		'@context': 'https://schema.org',
		'@type': 'ItemList',
		name: 'QuickNotch widgets',
		itemListElement: widgets.map((widget, index) => ({
			'@type': 'ListItem',
			position: index + 1,
			name: widget.label,
			description: widget.blurb
		}))
	};

	const themeListSchema = {
		'@context': 'https://schema.org',
		'@type': 'ItemList',
		name: 'QuickNotch notch themes',
		itemListElement: themes.map((theme, index) => ({
			'@type': 'ListItem',
			position: index + 1,
			name: theme.label,
			description: theme.blurb
		}))
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
	<meta name="googlebot" content="index, follow, max-image-preview:large" />
	<meta name="bingbot" content="index, follow, max-image-preview:large" />
	<meta name="author" content={site.author} />
	<meta name="keywords" content={site.keywords.join(', ')} />
	<meta name="application-name" content={site.name} />
	<meta name="generator" content="SvelteKit" />
	<meta name="referrer" content="strict-origin-when-cross-origin" />
	<meta name="format-detection" content="telephone=no" />
	<meta name="theme-color" content="#f9f3e7" />

	<meta property="og:type" content="website" />
	<meta property="og:site_name" content={site.name} />
	<meta property="og:title" content={title} />
	<meta property="og:description" content={site.description} />
	<meta property="og:url" content={canonical} />
	<meta property="og:locale" content="en_US" />
	<meta property="og:image" content={ogImage} />
	<meta property="og:image:width" content="1200" />
	<meta property="og:image:height" content="630" />
	<meta property="og:image:type" content="image/png" />
	<meta
		property="og:image:alt"
		content="QuickNotch: a floating notch at the top of a desktop that expands into a dock of pinned apps, with system, weather and calendar widgets, for Windows and macOS."
	/>

	<meta name="twitter:card" content="summary_large_image" />
	<meta name="twitter:title" content={title} />
	<meta name="twitter:description" content={site.shortDescription} />
	<meta name="twitter:image" content={ogImage} />
	<meta
		name="twitter:image:alt"
		content="QuickNotch: a floating notch at the top of a desktop that expands into a dock of pinned apps."
	/>

	{@html `<script type="application/ld+json">${serialise(softwareSchema)}<\/script>`}
	{@html `<script type="application/ld+json">${serialise(websiteSchema)}<\/script>`}
	{@html `<script type="application/ld+json">${serialise(organizationSchema)}<\/script>`}
	{@html `<script type="application/ld+json">${serialise(faqSchema)}<\/script>`}
	{@html `<script type="application/ld+json">${serialise(widgetListSchema)}<\/script>`}
	{@html `<script type="application/ld+json">${serialise(themeListSchema)}<\/script>`}
</svelte:head>
