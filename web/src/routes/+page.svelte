<script lang="ts">
	import Seo from '$components/Seo.svelte';
	import Nav from '$components/Nav.svelte';
	import Hero from '$components/Hero.svelte';
	import StatsStrip from '$components/StatsStrip.svelte';
	import WidgetsSection from '$components/WidgetsSection.svelte';
	import ThemesSection from '$components/ThemesSection.svelte';
	import LaunchSection from '$components/LaunchSection.svelte';
	import FaqSection from '$components/FaqSection.svelte';
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
		'Drag handle',
		'Always on top',
		'No account',
		'No telemetry',
		'Runs offline',
		'100% free',
		'More on the way'
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

	<FaqSection />

	<DownloadSection />
</main>

<Footer />

<style>
	/* Ticket-strip band between the stats and the manifesto: dashed edges top
	   and bottom, like perforated paper. */
	.strip {
		padding-block: clamp(1.5rem, 4vw, 3rem);
		border-block: 2px dashed var(--line-strong);
		background: var(--paper-deep);
		box-shadow: inset 0 8px 0 rgb(33 28 19 / 0.03);
	}
</style>
