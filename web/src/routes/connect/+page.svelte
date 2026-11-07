<script lang="ts">
	import Icon from '$components/Icon.svelte';
	import { calendarAuth, connectPage, site } from '$data/site';
	import CalendarNotchMock from '$components/CalendarNotchMock.svelte';
	import Footer from '$components/Footer.svelte';
	import { onMount } from 'svelte';

	let status = $state<'idle' | 'starting' | 'ready' | 'sent' | 'error'>('idle');
	let error = $state('');
	let debug = $state('');
	let consentUrl = $state('');

	/**
	 * Editorial consent page. The desktop sends state, client id and
	 * redirect; Google returns to /oauth/callback, which forwards to the
	 * loopback.
	 */
	onMount(() => {
		try {
			const params = new URLSearchParams(window.location.search);
			const state = params.get('state');
			const scope = params.get('scope');
			const accessType = params.get('access_type');
			const prompt = params.get('prompt');
			const codeChallenge = params.get('code_challenge');
			const codeChallengeMethod = params.get('code_challenge_method');
			// The client id arrives from the desktop rather than living here: the
			// site is public, the app is the only thing that needs to name its own
			// Google client, and a copy of it in this page would only be one more
			// value to keep in step.
			const clientId = params.get('client_id');
			if (!state || !codeChallenge || !clientId) {
				error = 'The desktop app sent an incomplete sign-in request.';
				status = 'error';
				return;
			}

			// Prefer the redirect_uri the desktop sent; this page's origin is the
			// local-dev fallback. The wrong URI fails as redirect_uri_mismatch.
			const redirectUri = params.get('redirect_uri') ?? (location.origin + '/oauth/callback');

			// The loopback port is not read from the query: it rides inside state,
			// the only value Google echoes back intact.
			const qs = new URLSearchParams({
				client_id: clientId,
				redirect_uri: redirectUri,
				response_type: 'code',
				scope: scope ?? calendarAuth.scope,
				access_type: accessType ?? 'offline',
				prompt: prompt ?? 'consent',
				state,
				code_challenge: codeChallenge,
				code_challenge_method: codeChallengeMethod ?? 'S256'
			});

			consentUrl = `${calendarAuth.authEndpoint}?${qs.toString()}`;
			debug = consentUrl;

			// `?standby=1` renders the page without handing off to Google: the
			// button becomes the trigger instead of a timer. It exists so the
			// flow can be walked through (and screenshotted) without starting a
			// real sign-in; the desktop never sends it.
			if (params.get('standby') === '1') {
				status = 'ready';
				return;
			}

			status = 'starting';
			setTimeout(() => {
				status = 'sent';
				window.location.assign(consentUrl);
			}, 160);
		} catch (e) {
			error = connectPage.errorParse;
			status = 'error';
		}
	});

	function openGoogle() {
		if (!consentUrl) return;
		window.location.assign(consentUrl);
	}
</script>

<svelte:head>
	<title>{connectPage.title} · {site.name}</title>
	<meta name="description" content={connectPage.description} />
	<!-- App-invoked OAuth step: reachable only from the desktop app's sign-in
	     button. Never indexed, never followed, never shown in search. The
	     X-Robots-Tag in _headers says the same thing at the HTTP layer. -->
	<meta name="robots" content="noindex, nofollow, noarchive" />
	<meta name="referrer" content="strict-origin-when-cross-origin" />
	<meta name="theme-color" content="#f9f3e7" />
</svelte:head>

<!-- A slim bar rather than the site Nav: this page is a step inside a flow,
     not a section of the landing page, and the marketing nav's section
     anchors have no target here. -->
<header class="bar">
	<div class="container bar__inner">
		<a class="brand" href={site.homePath} aria-label={site.homeLabel}>
			<span class="brand__mark"><Icon name="logo" size={18} /></span>
			{site.name}
		</a>
		<a class="bar__back" href={site.homePath}>
			<Icon name="arrow-left" size={15} />
			{connectPage.backToSite}
		</a>
	</div>
</header>

<main id="main" class="page">
	<section class="hero">
		<div class="container stack-lg">
			<div class="stack-md">
				<span class="eyebrow">{connectPage.eyebrow}</span>
				<h1 class="display">
					{connectPage.headline} <span class="hl">{connectPage.headlineHighlight}</span>
				</h1>
				<p class="lead">{connectPage.lead}</p>
			</div>

			<div class="mock stack-md">
				<CalendarNotchMock expanded connected={status !== 'error'} theme="pill" />
				<p class="text-muted mono">{connectPage.mockNote}</p>
			</div>

			<div class="card card--lift connect-card stack-lg">
				{#if status === 'idle'}
					<p class="text-muted">{connectPage.preparing}</p>
				{:else if status === 'ready'}
					<!-- Standby mode: the consent button is the trigger instead of
					   an automatic hop, so the page can be walked through safely. -->
					<div class="stack-md">
						<span class="eyebrow">{connectPage.ready.eyebrow}</span>
						<h2 class="h3">{connectPage.ready.title}</h2>
						<p class="text-muted">{connectPage.ready.body}</p>
					</div>
					<div class="cluster">
						<button class="btn btn--primary btn--lg" onclick={openGoogle}>
							<Icon name="arrow-right" size={18} />
							{connectPage.signIn}
						</button>
						<button class="btn btn--ghost" onclick={() => history.back()}>
							<Icon name="arrow-left" size={16} />
							{connectPage.backToApp}
						</button>
					</div>
				{:else if status === 'starting' || status === 'sent'}
					<div class="cluster">
						<span class="dot dot--pulse"></span>
						<p>
							{status === 'starting' ? connectPage.opening : connectPage.sent}
						</p>
					</div>
					<div class="cluster">
						<button class="btn btn--primary" onclick={openGoogle}>
							<Icon name="arrow-right" size={16} />
							{connectPage.openAgain}
						</button>
						<button class="btn btn--ghost" onclick={() => history.back()}>
							<Icon name="arrow-left" size={16} />
							{connectPage.backToApp}
						</button>
					</div>
				{:else if status === 'error'}
					<div class="stack-md">
						<span class="eyebrow">{connectPage.errorEyebrow}</span>
						<h2 class="h4">{error}</h2>
						<p class="text-muted">{connectPage.errorBody}</p>
					</div>
					<!-- No "try again" here on purpose: `consentUrl` is empty in this branch, so
					   that button was inert. The only way out is back through the app,
					   which is what mints the loopback port this page needs. -->
					<div class="cluster">
						<a class="btn btn--primary" href={site.homePath}>
							<Icon name="arrow-left" size={16} />
							{connectPage.backToWebsite}
						</a>
					</div>
				{/if}
				{#if debug && status !== 'error' && status !== 'idle'}
					<details class="debug mono text-muted">
						<summary>{connectPage.debugSummary}</summary>
						<code>{debug}</code>
					</details>
				{/if}
			</div>
		</div>
	</section>
</main>

<Footer />

<style>
	.bar {
		position: sticky;
		top: 0;
		z-index: 20;
		background: var(--paper);
		border-bottom: 2px solid var(--ink);
	}

	.bar__inner {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 1rem;
		height: var(--header-h);
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
		background: var(--ink);
		color: var(--paper-lift);
	}

	.bar__back {
		display: inline-flex;
		align-items: center;
		gap: 0.45rem;
		font-size: var(--fs-small);
		color: var(--text-2);
	}

	.bar__back:hover {
		color: var(--accent-text);
	}

	.page {
		padding-block: clamp(2.5rem, 5vw, 4rem) clamp(3rem, 7vw, 6rem);
	}

	.hero {
		padding-block: clamp(1rem, 3vw, 2rem) clamp(2rem, 6vw, 4rem);
	}

	.mock {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 1.25rem;
		padding-block: clamp(1.5rem, 4vw, 2.5rem);
		border-block: 2px dashed var(--line-strong);
		background: var(--paper-deep);
	}

	.connect-card {
		width: min(48rem, 100%);
		margin-inline: auto;
	}

	.dot {
		width: 0.85rem;
		height: 0.85rem;
		border-radius: 50%;
		background: var(--accent);
	}

	.dot--pulse {
		animation: pulse 1.8s infinite ease-in-out;
	}

	@keyframes pulse {
		0%,
		100% {
			box-shadow: 0 0 0 0 rgb(232 72 28 / 0.55);
		}
		50% {
			box-shadow: 0 0 0 0.5rem rgb(232 72 28 / 0);
		}
	}

	.debug {
		max-width: 100%;
		overflow: hidden;
	}

	.debug summary {
		cursor: pointer;
		margin-bottom: 0.75rem;
	}

	.debug code {
		display: block;
		padding: 0.85rem 1rem;
		border-radius: var(--r-sm);
		background: rgb(33 28 19 / 0.06);
		word-break: break-all;
		white-space: pre-wrap;
	}
</style>
