<script lang="ts">
	import Icon from './Icon.svelte';
	import { site } from '$data/site';

	// Prerendered at build time; the constant is a defensive fallback so a missing
	// or unusable Date can never crash the render.
	const FALLBACK_YEAR = 2025;
	const year = new Date().getFullYear() || FALLBACK_YEAR;
</script>

<footer class="footer">
	<div class="container footer__inner">
		<a class="brand" href="#top" aria-label="{site.name} — back to top">
			<span class="brand__mark"><Icon name="logo" size={18} /></span>
			{site.name}
		</a>

		<p class="footer__tagline">{site.tagline}</p>

		<p class="footer__bar">© {year} {site.author} · v{site.version}</p>
	</div>
</footer>

<style>
	.footer {
		position: relative;
		margin-top: clamp(3rem, 7vw, 6rem);
		border-top: 1px solid var(--line);
		background: rgb(15 23 42 / 0.02);
		padding-block: clamp(2.5rem, 5vw, 3.5rem);
	}

	.footer__inner {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 0.85rem;
		text-align: center;
	}

	.brand {
		display: inline-flex;
		align-items: center;
		gap: 0.6rem;
		font-family: var(--font-display);
		font-weight: 700;
		font-size: 1.08rem;
		letter-spacing: -0.02em;
		color: var(--text);
	}

	.brand__mark {
		display: grid;
		place-items: center;
		width: 32px;
		height: 32px;
		border-radius: 10px;
		color: #fff;
		background: var(--accent);
		box-shadow: 0 8px 22px -10px rgb(79 70 229 / 0.4);
	}

	.footer__tagline {
		max-width: 34ch;
		font-size: var(--fs-small);
		color: var(--text-3);
	}

	.footer__bar {
		margin-top: 0.5rem;
		font-size: var(--fs-small);
		color: var(--text-3);
	}
</style>
