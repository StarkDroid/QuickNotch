<script lang="ts">
	import Footer from '$components/Footer.svelte';
	import Icon from '$components/Icon.svelte';
	import { oauthPage } from '$data/site';
	import { onMount, onDestroy } from 'svelte';

	/**
	 * Static page: forwards the query to the loopback, which validates state
	 * and performs the token exchange.
	 */
	let status = $state<'idle' | 'jumping' | 'done' | 'refused' | 'error'>('idle');
	let error = $state('');
	let target = $state('');

	/**
	 * How long the refusal page lingers before handing control back to the app.
	 * Long enough to read why, short enough that the settings widget — which is
	 * the authoritative place the failure is reported — is never left spinning.
	 */
	const REFUSAL_HANDOFF_MS = 2500;
	let handoff: ReturnType<typeof setTimeout> | undefined;

	// Hoisted out of `onMount` so the "back to QuickNotch now" button can call it:
	// it is both the success path and the refusal path's escape hatch.
	let returnToApp = $state<() => void>(() => {});

	/**
	 * state is base64url JSON with the port and nonce. Only the port is read
	 * here; the desktop verifies the whole string.
	 */
	function extractPortFromState(state: string | null): number | null {
		if (!state) return null;
		try {
			let b64 = state.replace(/-/g, '+').replace(/_/g, '/');
			while (b64.length % 4 !== 0) b64 += '=';
			const obj = JSON.parse(new TextDecoder().decode(Uint8Array.from(atob(b64), (c) => c.charCodeAt(0))));
			const port = Number(obj?.p);
			return Number.isInteger(port) ? port : null;
		} catch {
			// Unparseable state: the desktop would reject the callback anyway,
			// so say so plainly rather than jumping at a guessed port.
			return null;
		}
	}

	onMount(() => {
		const params = new URLSearchParams(window.location.search);
		const code = params.get('code');
		const state = params.get('state');
		const err = params.get('error');
		const errDesc = params.get('error_description');

		const port = state ? extractPortFromState(state) : null;
		const hasPort = port !== null && port >= 1 && port <= 65535;

		/**
		 * Forward refusals too, not just codes: the desktop tells Cancel from a
		 * Testing consent screen.
		 */
		returnToApp = function () {
			if (!hasPort || !state) return;
			target = `http://127.0.0.1:${port}/oauth?${params.toString()}`;
			status = 'jumping';
			window.location.assign(target);
			status = 'done';
		}

		if (err) {
			error = oauthPage.errors.refused(err, errDesc ?? '');
			if (!hasPort) {
				// Nowhere to hand the failure back to, so the page has to be the
				// whole answer.
				status = 'error';
				return;
			}
			status = 'refused';
			handoff = setTimeout(returnToApp, REFUSAL_HANDOFF_MS);
			return;
		}
		if (!code) {
			error = oauthPage.errors.noCode;
			status = 'error';
			return;
		}
		if (!state) {
			error = oauthPage.errors.noState;
			status = 'error';
			return;
		}
		if (!hasPort) {
			error = oauthPage.errors.noPort;
			status = 'error';
			return;
		}

		// Reattach the query to the loopback. The GET is the signal the desktop's
		// ServerSocket is waiting for; it will respond with the small success
		// page and complete the handshake.
		returnToApp();
	});

	onDestroy(() => clearTimeout(handoff));
</script>

<svelte:head>
	<title>{oauthPage.title}</title>
	<meta name="description" content={oauthPage.description} />
	<!-- Second half of the app-invoked OAuth flow. Never indexed — see _headers. -->
	<meta name="robots" content="noindex, nofollow, noarchive" />
	<meta name="referrer" content="strict-origin-when-cross-origin" />
	<meta name="theme-color" content="#f9f3e7" />
</svelte:head>

<main class="page">
	<section class="section">
		<div class="container-narrow stack-lg">
			<div class="card card--lift stack-lg">
				{#if status === 'idle' || status === 'jumping' || status === 'done'}
					<div class="cluster">
						<span class="dot dot--pulse"></span>
						<h1 class="h4">{oauthPage.returning}</h1>
					</div>
					<p class="text-muted">{oauthPage.returningBody}</p>
					{#if target}
						<code class="mono text-muted jump">{target}</code>
					{/if}
{:else if status === 'refused'}
					<span class="eyebrow">{oauthPage.refusedEyebrow}</span>
					<h1 class="h4">{error}</h1>
					<p class="text-muted">{oauthPage.refusedBody}</p>
					<button class="btn" type="button" onclick={returnToApp}>

						<Icon name="arrow-left" size={16} />

						{oauthPage.backNow}
					</button>

				{:else if status === 'error'}
					<span class="eyebrow">{oauthPage.errorEyebrow}</span>
					<h1 class="h4">{error}</h1>
					<p class="text-muted">{oauthPage.errorBody}</p>
					<a class="btn btn--ghost" href="/connect" data-sveltekit-reload>

						<Icon name="arrow-left" size={16} />

						{oauthPage.startAgain}
					</a>

				{/if}
			</div>
		</div>
	</section>
</main>

<Footer />

<style>
	.page {
		padding-block: clamp(6rem, 12vw, 10rem) clamp(3rem, 7vw, 6rem);
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

	.jump {
		display: block;
		padding: 0.85rem 1rem;
		border-radius: var(--r-sm);
		background: rgb(33 28 19 / 0.06);
		word-break: break-all;
		white-space: pre-wrap;
	}
</style>
