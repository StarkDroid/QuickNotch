<script lang="ts">
	import Footer from '$components/Footer.svelte';
	import Icon from '$components/Icon.svelte';
	import Nav from '$components/Nav.svelte';
	import PageSeo from '$components/PageSeo.svelte';
	import { notFound, site } from '$data/site';

	/**
	 * Written to 404.html and served by Cloudflare for unmatched paths, so a
	 * dead link is a real 404 rather than the homepage.
	 */
</script>

<PageSeo title={notFound.title} description={notFound.lead} path="/404" index={false} />

<Nav />

<main id="main">
	<section class="section missing" id="top">
		<div class="container container-narrow">
			<header class="section-head">
				<p class="eyebrow">{notFound.eyebrow}</p>
				<h1 class="display h2">{notFound.title}</h1>
				<p class="lead">{notFound.lead}</p>
			</header>

			<div class="missing__actions">
				<a class="btn btn--primary" href={site.homePath}>
					<Icon name="arrow-left" size={17} />
					{notFound.home}
				</a>
				<a class="btn btn--ghost" href={site.downloadPath}>
					<Icon name="download" size={17} />
					{notFound.download}
				</a>
			</div>
		</div>
	</section>
</main>

<Footer />

<style>
	.missing {
		padding-top: clamp(2rem, 5vw, 3.5rem);
	}

	.missing .section-head {
		margin-bottom: clamp(1.5rem, 3vw, 2.25rem);
	}

	.missing__actions {
		display: flex;
		flex-wrap: wrap;
		gap: 0.9rem;
	}
</style>
