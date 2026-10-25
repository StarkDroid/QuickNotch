<script lang="ts">
	import Icon from './Icon.svelte';
	import { pricing, site } from '$data/site';

	// Prerendered at build time; the constant is a defensive fallback so a missing
	// or unusable Date can never crash the render.
	const FALLBACK_YEAR = 2025;
	const year = new Date().getFullYear() || FALLBACK_YEAR;
</script>

<footer class="footer">
	<div class="container footer__inner">
		<a class="brand" href="#top" aria-label="{site.name}, back to top">
			<span class="brand__mark"><Icon name="logo" size={18} /></span>
			{site.name}
		</a>

		<p class="footer__tagline">{site.tagline}</p>

		<nav class="footer__links" aria-label="More">
			<a href={site.purchaseUrl} target="_blank" rel="noopener noreferrer">
				Buy — {pricing.label}
			</a>
			<a href="{site.url}">Back to top</a>
		</nav>

		<p class="footer__bar">
			<span>© {year} {site.author} · v{site.version}</span>
			<span aria-hidden="true">· printed on cream, obviously</span>
		</p>
	</div>
</footer>

<style>
	.footer {
		position: relative;
		margin-top: clamp(3rem, 7vw, 6rem);
		border-top: 2px dashed var(--line-strong);
		background: var(--paper-deep);
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
		font-size: 1.15rem;
		letter-spacing: -0.02em;
		color: var(--text);
	}

	.brand__mark {
		display: grid;
		place-items: center;
		width: 32px;
		height: 32px;
		border-radius: var(--r-sm);
		border: 2px solid var(--ink);
		color: var(--on-accent);
		background: var(--accent);
		box-shadow: 2px 2px 0 rgb(33 28 19 / 0.85);
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

	.footer__links {
		display: flex;
		flex-wrap: wrap;
		justify-content: center;
		gap: 0.5rem 1.25rem;
		font-size: var(--fs-small);
		color: var(--text-2);
	}

	.footer__links a {
		text-decoration: underline;
		text-decoration-color: var(--line-strong);
		text-underline-offset: 3px;
	}

	.footer__links a:hover {
		color: var(--accent-text);
		text-decoration-color: var(--accent);
	}
</style>
