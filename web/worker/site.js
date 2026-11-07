/**
 * The site's only edge code.
 *
 * Cloudflare will only issue a certificate and a DNS record for a hostname that
 * belongs to a Worker, so the site is deployed as one: static assets plus this
 * handler. All it does is keep a single public address. `www.quicknotch.app`
 * answers with a 301 to the same path on the apex, a plain `http` request is
 * upgraded to `https`, and everything else (the `workers.dev` address,
 * pull-request previews) is handed straight back to the static assets. Both
 * corrections happen in one hop, and the query string survives.
 *
 * Canonical links, the sitemap and the structured data all name the apex, so
 * there is exactly one address for the site and previews still show what the
 * branch actually built.
 */
const APEX = 'quicknotch.app';
const WWW = `www.${APEX}`;

export default {
	async fetch(request, env) {
		const url = new URL(request.url);
		const wrongHost = url.hostname === WWW;
		const insecure = url.protocol === 'http:';

		if (wrongHost || insecure) {
			if (wrongHost) url.hostname = APEX;
			url.protocol = 'https:';
			return Response.redirect(url.toString(), 301);
		}

		return env.ASSETS.fetch(request);
	}
};
