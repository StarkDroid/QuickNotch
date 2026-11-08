<script lang="ts">
	import LegalPage from '$components/LegalPage.svelte';
	import PageSeo from '$components/PageSeo.svelte';
	import { privacy, site } from '$data/site';
</script>

<PageSeo title={privacy.seo.title} description={privacy.seo.description} path={site.privacyPath} />
<LegalPage doc={privacy} />
