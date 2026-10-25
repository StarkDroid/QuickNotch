<script lang="ts">
	import Seo from '$components/Seo.svelte';
	import Nav from '$components/Nav.svelte';
	import Hero from '$components/Hero.svelte';
	import TourSection from '$components/TourSection.svelte';
	import LookSection from '$components/LookSection.svelte';
	import PricingSection from '$components/PricingSection.svelte';
	import FaqSection from '$components/FaqSection.svelte';
	import Footer from '$components/Footer.svelte';

	/**
	 * One tour instead of a widget section, a gaming section, a theme section and
	 * a launch section.
	 *
	 * The page now reads: the promise, the whole product in one scroll, the two
	 * knobs you can turn, the price, then the questions. Everything a widget does
	 * is explained once, where it is animated, so there is no second pass to
	 * skim — and the FAQ is the last word, not a download detour.
	 */
</script>

<Seo />

<Nav />

<main id="main">
	<Hero />

	<TourSection />

	<LookSection />

	<PricingSection />

	<FaqSection />

</main>

<Footer />
