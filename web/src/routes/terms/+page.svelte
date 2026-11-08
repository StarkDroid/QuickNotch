<script lang="ts">
	import LegalPage from '$components/LegalPage.svelte';
	import PageSeo from '$components/PageSeo.svelte';
	import { site, terms } from '$data/site';
</script>

<PageSeo title={terms.seo.title} description={terms.seo.description} path={site.termsPath} />
<LegalPage doc={terms} />
