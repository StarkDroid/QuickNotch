<script lang="ts">
	import Icon from './Icon.svelte';
	import { buyCta, nav, site } from '$data/site';
	import { scrollToId } from '$lib/motion/smoothScroll';

	let scrolled = $state(false);
	let menuOpen = $state(false);
	let progress = $state(0);
	let activeHref = $state<string>('');

	$effect(() => {
		let frame = 0;
		const update = () => {
			frame = 0;
			scrolled = window.scrollY > 18;
			const max = document.documentElement.scrollHeight - window.innerHeight;
			progress = max > 0 ? Math.min(1, window.scrollY / max) : 0;
		};
		const onScroll = () => {
			if (!frame) frame = requestAnimationFrame(update);
		};
		update();
		window.addEventListener('scroll', onScroll, { passive: true });
		window.addEventListener('resize', onScroll);
		return () => {
			if (frame) cancelAnimationFrame(frame);
			window.removeEventListener('scroll', onScroll);
			window.removeEventListener('resize', onScroll);
		};
	});

	// Highlight the section currently in view.
	$effect(() => {
		const ids = nav.map((item) => item.href.slice(1));
		const sections = ids
			.map((id) => document.getElementById(id))
			.filter((el): el is HTMLElement => Boolean(el));
		if (!sections.length) return;

		const observer = new IntersectionObserver(
			(entries) => {
				const visible = entries
					.filter((entry) => entry.isIntersecting)
					.sort((a, b) => b.intersectionRatio - a.intersectionRatio)[0];
				if (visible) activeHref = `#${visible.target.id}`;
			},
			{ rootMargin: '-45% 0px -45% 0px', threshold: [0, 0.2, 0.5, 1] }
		);
		sections.forEach((section) => observer.observe(section));
		return () => observer.disconnect();
	});

	function go(event: MouseEvent, href: string) {
		if (!href.startsWith('#')) return;
		event.preventDefault();
		menuOpen = false;
		scrollToId(href.slice(1));
	}

	function onKeydown(event: KeyboardEvent) {
		if (event.key === 'Escape') menuOpen = false;
	}
</script>

<svelte:window onkeydown={onKeydown} />

<a class="skip-link" href="#main">Skip to content</a>

<header class="nav" class:scrolled class:open={menuOpen}>
	<div class="nav__bar">
		<a class="brand" href="#top" onclick={(event) => go(event, '#top')} aria-label="{site.name} home">
			<span class="brand__mark"><Icon name="logo" size={20} /></span>
			{site.name}
		</a>

		<nav class="nav__links" aria-label="Sections">
			{#each nav as item (item.href)}
				<a
					href={item.href}
					class="nav__link"
					class:active={activeHref === item.href}
					aria-current={activeHref === item.href ? 'true' : undefined}
					onclick={(event) => go(event, item.href)}>{item.label}</a
				>
			{/each}
		</nav>

		<div class="nav__actions">
			<a
				class="btn btn--primary btn--sm"
				href={site.purchaseUrl}
				data-magnetic
				target="_blank"
				rel="noopener noreferrer"
			>
				<Icon name="cart" size={16} />
				{buyCta}
			</a>
			<button
				class="nav__toggle"
				type="button"
				aria-expanded={menuOpen}
				aria-controls="mobile-menu"
				aria-label={menuOpen ? 'Close menu' : 'Open menu'}
				onclick={() => (menuOpen = !menuOpen)}
			>
				<Icon name={menuOpen ? 'close' : 'menu'} size={20} />
			</button>
		</div>
	</div>

	<div id="mobile-menu" class="nav__mobile" hidden={!menuOpen}>
		{#each nav as item (item.href)}
			<a href={item.href} onclick={(event) => go(event, item.href)}>{item.label}</a>
		{/each}
		<a class="btn btn--primary btn--block" href={site.purchaseUrl} target="_blank" rel="noopener noreferrer">
			<Icon name="cart" size={16} />
			{buyCta} · Windows &amp; macOS
		</a>
	</div>

	<span class="nav__progress" style:transform="scaleX({progress})" aria-hidden="true"></span>
</header>

<style>
	.nav {
		position: sticky;
		top: 0;
		z-index: 100;
		transition: background-color var(--dur) var(--ease-out), border-color var(--dur) var(--ease-out),
			box-shadow var(--dur) var(--ease-out);
		border-bottom: 2px solid transparent;
	}

	/* On a cream page the header opens transparent, then slips onto a solid paper
	   sheet with a hard print border once you scroll — no frosted glass. */
	.nav.scrolled,
	.nav.open {
		background: var(--paper-lift);
		border-bottom-color: var(--ink);
		box-shadow: 0 5px 0 rgb(33 28 19 / 0.1);
	}

	.nav__bar {
		max-width: var(--maxw);
		margin-inline: auto;
		padding: 0.7rem var(--gutter);
		display: flex;
		align-items: center;
		gap: 1.25rem;
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
		flex: none;
	}

	.brand__mark {
		display: grid;
		place-items: center;
		width: 34px;
		height: 34px;
		border-radius: var(--r-sm);
		border: 2px solid var(--ink);
		color: var(--on-accent);
		background: var(--accent);
		box-shadow: 3px 3px 0 rgb(33 28 19 / 0.9);
	}

	.nav__links {
		display: none;
		align-items: center;
		gap: 0.25rem;
		margin-inline: auto;
	}

	.nav__link {
		position: relative;
		padding: 0.5rem 0.8rem;
		border-radius: var(--r-sm);
		font-size: 0.9rem;
		color: var(--text-2);
		transition: color var(--dur-fast) var(--ease-out), background-color var(--dur-fast) var(--ease-out);
	}

	.nav__link:hover {
		color: var(--text);
		background: var(--paper-deep);
	}

	.nav__link.active {
		color: var(--text);
		font-weight: 600;
	}

	.nav__link.active::after {
		content: '';
		position: absolute;
		left: 50%;
		bottom: 0.15rem;
		translate: -50% 0;
		width: 14px;
		height: 2px;
		background: var(--accent);
	}

	.nav__actions {
		display: flex;
		align-items: center;
		gap: 0.5rem;
		margin-left: auto;
	}

	.nav__toggle {
		display: grid;
		place-items: center;
		width: 40px;
		height: 40px;
		border-radius: var(--r-sm);
		border: 2px solid var(--ink);
		color: var(--text);
		background: var(--paper-lift);
		box-shadow: 2px 2px 0 rgb(33 28 19 / 0.85);
	}

	.nav__mobile {
		display: flex;
		flex-direction: column;
		gap: 0.2rem;
		padding: 0.5rem var(--gutter) 1.2rem;
		border-top: 2px solid var(--ink);
		background: var(--paper-lift);
	}

	.nav__mobile[hidden] {
		display: none;
	}

	.nav__mobile > a:not(.btn) {
		padding: 0.75rem 0.25rem;
		font-size: 1.05rem;
		color: var(--text-2);
		border-bottom: 1px dashed var(--line-strong);
	}

	.nav__mobile > a:not(.btn):hover {
		color: var(--text);
	}

	.nav__mobile .btn {
		margin-top: 1rem;
	}

	.nav__progress {
		position: absolute;
		left: 0;
		bottom: -2px;
		width: 100%;
		height: 3px;
		transform-origin: 0 50%;
		background: var(--accent);
		opacity: 0.9;
	}

	@media (min-width: 900px) {
		.nav__links {
			display: flex;
		}

		.nav__toggle,
		.nav__mobile {
			display: none !important;
		}
	}
</style>
