<script lang="ts">
	import Seo from '$components/Seo.svelte';
	import Icon from '$components/Icon.svelte';
	import { site } from '$data/site';
	import CalendarNotchMock from '$components/CalendarNotchMock.svelte';
	import Footer from '$components/Footer.svelte';
	import { calendarAuth } from '$data/site';
	import { onMount } from 'svelte';

	let status = $state<'idle' | 'starting' | 'ready' | 'sent' | 'error'>('idle');
	let error = $state('');
	let debug = $state('');
	let consentUrl = $state('');

	/**
	 * The connect page owns the consent UI: it's editorial (retro poster), not a
	 * system dialog. The desktop passes us the parameters it needs to assemble
	 * Google's auth URL (`state` already contains the loopback port), and we
	 * send the user there. Google's reply bounces the browser to `/oauth/callback`
	 * on this site, which then jumps home to `http://127.0.0.1:<port>/...` where
	 * the token exchange happens with the secret that never leaves the app.
	 */
	onMount(() => {
		try {
			const params = new URLSearchParams(window.location.search);
			const port = params.get('port');
			const state = params.get('state');
			const scope = params.get('scope');
			const accessType = params.get('access_type');
			const prompt = params.get('prompt');
			const codeChallenge = params.get('code_challenge');
			const codeChallengeMethod = params.get('code_challenge_method');
			if (!state || !codeChallenge) {
				error = 'The desktop app sent an incomplete sign-in request.';
				status = 'error';
				return;
			}

			const qs = new URLSearchParams({
				client_id: calendarAuth.clientId,
				redirect_uri: calendarAuth.callbackUrl,
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
			error = 'Could not parse the sign-in request from the desktop app.';
			status = 'error';
		}
	});

	function openGoogle() {
		if (!consentUrl) return;
		window.location.assign(consentUrl);
	}
</script>

<Seo />

<!-- A slim bar rather than the site Nav: this page is a step inside a flow,
     not a section of the landing page, and the marketing nav's section
     anchors have no target here. -->
<header class="bar">
	<div class="container bar__inner">
		<a class="brand" href="/">
			<span class="brand__mark"><Icon name="logo" size={18} /></span>
			{site.name}
		</a>
		<a class="bar__back" href="/">
			<Icon name="arrow-left" size={15} />
			Back to site
		</a>
	</div>
</header>

<main id="main" class="page">
	<section class="hero">
		<div class="container stack-lg">
			<div class="stack-md">
				<span class="eyebrow">Google Calendar · read-only</span>
				<h1 class="display">
					Connect QuickNotch <span class="hl">to your calendar</span>
				</h1>
				<p class="lead">
					The notch shows today's upcoming events at a glance, and scrolls the
					next one as a live ticker — without ever needing to open Google
					Calendar. The connection is read-only, stays on your machine, and
					never sends your events anywhere else.
				</p>
			</div>

			<div class="mock stack-md">
				<CalendarNotchMock expanded connected={status !== 'error'} theme="pill" />
				<p class="text-muted mono">You will land back in QuickNotch after granting access.</p>
			</div>

			<div class="card card--lift connect-card stack-lg">
				{#if status === 'idle'}
					<p class="text-muted">Preparing your sign-in…</p>
				{:else if status === 'ready'}
					<!-- Standby mode: the consent button is the trigger instead of
					   an automatic hop, so the page can be walked through safely. -->
					<div class="stack-md">
						<span class="eyebrow">Ready when you are</span>
						<h2 class="h3">Sign in with Google to link your calendar</h2>
						<p class="text-muted">
							QuickNotch only reads your calendar. QuickNotch never uploads your
							events anywhere, and you can disconnect any time from Settings.
						</p>
					</div>
					<div class="cluster">
						<button class="btn btn--primary btn--lg" onclick={openGoogle}>
							<Icon name="arrow-right" size={18} />
							Sign in with Google
						</button>
						<button class="btn btn--ghost" onclick={() => history.back()}>
							<Icon name="arrow-left" size={16} />
							Back to QuickNotch
						</button>
					</div>
				{:else if status === 'starting' || status === 'sent'}
					<div class="cluster">
						<span class="dot dot--pulse"></span>
						<p>
							{status === 'starting'
								? 'Opening Google consent…'
								: 'Taking you to Google — if the tab did not open, try again.'}
						</p>
					</div>
					<div class="cluster">
						<button class="btn btn--primary" onclick={openGoogle}>
							<Icon name="arrow-right" size={16} />
							Open Google again
						</button>
						<button class="btn btn--ghost" onclick={() => history.back()}>
							<Icon name="arrow-left" size={16} />
							Back to QuickNotch
						</button>
					</div>
				{:else if status === 'error'}
					<div class="stack-md">
						<span class="eyebrow">Something needs a fix</span>
						<h2 class="h4">{error}</h2>
						<p class="text-muted">
							QuickNotch expects to hand this flow off to a small listener on your
							own machine. If the link expired, or you opened this page directly,
							start again from QuickNotch → Settings → Google Calendar.
						</p>
					</div>
					<div class="cluster">
						<button class="btn btn--primary" onclick={openGoogle}>
							<Icon name="arrow-right" size={16} />
							Try opening Google again
						</button>
						<a class="btn btn--ghost" href="/">
							<Icon name="arrow-left" size={16} />
							Back to the website
						</a>
					</div>
				{/if}
				{#if debug && status !== 'error' && status !== 'idle'}
					<details class="debug mono text-muted">
						<summary>Technical details (for debugging)</summary>
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