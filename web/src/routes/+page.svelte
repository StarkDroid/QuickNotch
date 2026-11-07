<script lang="ts">
	import Seo from '$components/Seo.svelte';
	import Nav from '$components/Nav.svelte';
	import Hero from '$components/Hero.svelte';
	import TourSection from '$components/TourSection.svelte';
	import LookSection from '$components/LookSection.svelte';
	import OfferSection from '$components/OfferSection.svelte';
	import FaqSection from '$components/FaqSection.svelte';
	import Footer from '$components/Footer.svelte';

	/**
	 * One tour replaces the widget, gaming, theme and launch sections; the
	 * FAQ closes the page.
	 */
</script>

<Seo />

<Nav />

<main id="main">
	<Hero />

	<TourSection />

	<LookSection />

	<OfferSection />

	<FaqSection />

</main>

<Footer />
