<script lang="ts">
	import Footer from '$components/Footer.svelte';
	import Icon from '$components/Icon.svelte';
	import Nav from '$components/Nav.svelte';
	import PageSeo from '$components/PageSeo.svelte';
	import { notFound, site } from '$data/site';

	/**
	 * The page behind a wrong address.
	 *
	 * The static adapter writes this route to `404.html` at the root of the
	 * build. Cloudflare answers any unmatched path with that file and a real 404
	 * status, so a mistyped or dead link looks like a missing page instead of the
	 * homepage pretending everything is fine. It is deliberately unlinked from
	 * the rest of the site and kept out of the sitemap; the two ways out are the
	 * only things it offers.
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
