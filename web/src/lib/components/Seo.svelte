<script lang="ts">
	import { latest } from '$data/releases';
	import { facts, faq, offer, platforms, seo, site, themes, tour } from '$data/site';

	/**
	 * Document-level SEO derived from the same content module the page
	 * renders, so structured data cannot drift from the copy.
	 */

	const title = seo.title;
	const description = seo.description;
	const canonical = `${site.url}/`;
	const ogImage = `${site.url}/og.png`;
	const logo = `${site.url}/icon-512.png`;

	const osList = platforms.map((platform) => platform.requirement).join(', ');

	const softwareSchema = {
		'@context': 'https://schema.org',
		'@type': 'SoftwareApplication',
		'@id': `${site.url}/#software`,
		name: site.name,
		alternateName: seo.alternateName,
		applicationCategory: seo.applicationCategory,
		applicationSubCategory: seo.applicationSubCategory,
		operatingSystem: osList,
		softwareVersion: latest,
		downloadUrl: site.downloadUrl,
		installUrl: site.downloadUrl,
		fileFormat: 'application/octet-stream',
		license: 'https://opensource.org/license/mit',
		sameAs: [site.repository],
		isAccessibleForFree: true,
		description: description,
		inLanguage: 'en',
		keywords: site.keywords.join(', '),
		screenshot: ogImage,
		releaseNotes: `${site.url}/changelog`,
		softwareHelp: site.issuesUrl,
		offers: {
			'@type': 'Offer',
			price: offer.price.amount,
			priceCurrency: offer.price.currency,
			availability: 'https://schema.org/InStock',
			url: site.downloadUrl
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
		featureList: [...seo.featureList],
		// Interesting, indexable facts rather than generic marketing filler.
		additionalProperty: facts.map((fact) => ({
			'@type': 'PropertyValue',
			name: fact.label,
			value: fact.value
		}))
	};

	const websiteSchema = {
		'@context': 'https://schema.org',
		'@type': 'WebSite',
		'@id': `${site.url}/#website`,
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

	const webPageSchema = {
		'@context': 'https://schema.org',
		'@type': 'WebPage',
		'@id': `${canonical}#webpage`,
		url: canonical,
		name: title,
		description: description,
		inLanguage: 'en',
		isPartOf: { '@id': `${site.url}/#website` },
		about: { '@id': `${site.url}/#software` },
		primaryImageOfPage: { '@type': 'ImageObject', url: ogImage }
	};

	const organizationSchema = {
		'@context': 'https://schema.org',
		'@type': 'Organization',
		'@id': `${site.url}/#organization`,
		name: site.author,
		url: site.url,
		logo: { '@type': 'ImageObject', url: logo },
		description: seo.organizationDescription,
		sameAs: [site.repository]
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

	/** ItemList of the tour's states, for entity-rich crawlable signals. */
	const widgetListSchema = {
		'@context': 'https://schema.org',
		'@type': 'ItemList',
		name: seo.widgetListName,
		itemListElement: tour.steps.map((step, index) => ({
			'@type': 'ListItem',
			position: index + 1,
			name: step.label,
			description: step.body
		}))
	};

	const themeListSchema = {
		'@context': 'https://schema.org',
		'@type': 'ItemList',
		name: seo.themeListName,
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
	<meta name="description" content={description} />
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
	<meta property="og:description" content={description} />
	<meta property="og:url" content={canonical} />
	<meta property="og:locale" content="en_US" />
	<meta property="og:image" content={ogImage} />
	<meta property="og:image:width" content="1200" />
	<meta property="og:image:height" content="630" />
	<meta property="og:image:type" content="image/png" />
	<meta
		property="og:image:alt"
		content={seo.ogImageAlt}
	/>

	<meta name="twitter:card" content="summary_large_image" />
	<meta name="twitter:title" content={title} />
	<meta name="twitter:description" content={description} />
	<meta name="twitter:image" content={ogImage} />
	<meta
		name="twitter:image:alt"
		content={seo.twitterImageAlt}
	/>

	{@html `<script type="application/ld+json">${serialise(softwareSchema)}<\/script>`}
	{@html `<script type="application/ld+json">${serialise(websiteSchema)}<\/script>`}
	{@html `<script type="application/ld+json">${serialise(webPageSchema)}<\/script>`}
	{@html `<script type="application/ld+json">${serialise(organizationSchema)}<\/script>`}
	{@html `<script type="application/ld+json">${serialise(faqSchema)}<\/script>`}
	{@html `<script type="application/ld+json">${serialise(widgetListSchema)}<\/script>`}
	{@html `<script type="application/ld+json">${serialise(themeListSchema)}<\/script>`}
</svelte:head>
