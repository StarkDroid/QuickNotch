<script lang="ts">
	import Seo from '$components/Seo.svelte';
	import Nav from '$components/Nav.svelte';
	import Hero from '$components/Hero.svelte';
	import StatsStrip from '$components/StatsStrip.svelte';
	import WidgetsSection from '$components/WidgetsSection.svelte';
	import ThemesSection from '$components/ThemesSection.svelte';
	import LaunchSection from '$components/LaunchSection.svelte';
	import DownloadSection from '$components/DownloadSection.svelte';
	import Footer from '$components/Footer.svelte';
	import Marquee from '$components/Marquee.svelte';
	import ManifestoSection from '$components/ManifestoSection.svelte';

	const marqueeItems = [
		'Quick launch',
		'Weather',
		'System stats',
		'Game FPS',
		'Live activities',
		'AMOLED notch',
		'Floating pill',
		'Drag handle'
	];
</script>

<Seo />

<Nav />

<main id="main">
	<Hero />

	<StatsStrip />

	<div class="strip">
		<Marquee items={marqueeItems} duration={38} />
	</div>

	<ManifestoSection />

	<WidgetsSection />

	<ThemesSection />

	<LaunchSection />

	<DownloadSection />
</main>

<Footer />

<style>
	.strip {
		padding-block: clamp(1.5rem, 4vw, 3rem);
		border-block: 1px solid var(--line);
		background: rgb(15 23 42 / 0.02);
	}
</style>
