<script lang="ts">
	import Seo from '$components/Seo.svelte';
	import Footer from '$components/Footer.svelte';
	import Icon from '$components/Icon.svelte';
	import { onMount } from 'svelte';

	/**
	 * Google's OAuth bounce lands here (the redirect URI registered in the
	 * console). This is a static page, so it cannot exchange the code — that is
	 * the secret-bearing desktop app. Instead, it takes the query (code/state)
	 * and performs a top-level navigation to the loopback landing strip the
	 * desktop spun up: `http://127.0.0.1:<port>/oauth` with the same query.
	 * The desktop's ServerSocket accepts that GET, reads the code+state (state
	 * must match the exact string this attempt emitted), sends the small
	 * consent page back, and completes the exchange.
	 */
	let status = $state<'idle' | 'jumping' | 'done' | 'error'>('idle');
	let error = $state('');
	let target = $state('');

	/**
	 * The loopback port rides inside the `state` the desktop minted: it is
	 * base64url-encoded JSON `{"p":<port>,"s":"<nonce>"}`. Only `p` matters
	 * here — the desktop verifies the whole string it sees against the one it
	 * sent, so this page only needs the port out of it and must not treat the
	 * nonce as authority for anything.
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

		if (err) {
			const desc = errDesc ? ` (${errDesc})` : '';
			error = `Google returned "${err}"${desc}.`;
			status = 'error';
			return;
		}
		if (!code) {
			error = 'Google did not send an authorization code back.';
			status = 'error';
			return;
		}
		if (!state) {
			error = 'The callback is missing the state parameter.';
			status = 'error';
			return;
		}

		const port = extractPortFromState(state);
		if (!port || port < 1 || port > 65535) {
			error = 'The state does not tell QuickNotch which port to connect to.';
			status = 'error';
			return;
		}

		// Reattach the query to the loopback. The GET is the signal the desktop's
		// ServerSocket is waiting for; it will respond with the small success
		// page and complete the handshake.
		const qs = new URLSearchParams(params);
		target = `http://127.0.0.1:${port}/oauth?${qs.toString()}`;
		status = 'jumping';
		// A top-level location.assign is the cleanest way to jump back to a
		// non-http(s) loopback origin; the browser will navigate that tab to it.
		window.location.assign(target);
		status = 'done';
	});
</script>

<Seo />

<main class="page">
	<section class="section">
		<div class="container-narrow stack-lg">
			<div class="card card--lift stack-lg">
				{#if status === 'idle' || status === 'jumping' || status === 'done'}
					<div class="cluster">
						<span class="dot dot--pulse"></span>
						<h1 class="h4">Returning to QuickNotch…</h1>
					</div>
					<p class="text-muted">
						Google gave QuickNotch permission. We're sending you back to the app
						so it can finish signing you in. You can close this tab once
						QuickNotch says it's connected.
					</p>
					{#if target}
						<code class="mono text-muted jump">{target}</code>
					{/if}
				{:else if status === 'error'}
					<span class="eyebrow">Could not return to QuickNotch</span>
					<h1 class="h4">{error}</h1>
					<p class="text-muted">
						Try connecting again from QuickNotch → Settings → Google Calendar.
						If this keeps happening, the state from the desktop app might have
						expired or the browser blocked the loopback jump.
					</p>
					<a class="btn btn--ghost" href="/">
						<Icon name="arrow-left" size={16} />
						Back to QuickNotch
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