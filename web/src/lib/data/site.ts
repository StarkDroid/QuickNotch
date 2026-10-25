/**
 * Single source of truth for every string, link and structured-data value on
 * the site. Keeping content out of components makes the copy easy to audit for
 * SEO (crawlable text is prerendered from here) and trivial to update.
 */

export const site = {
	name: 'QuickNotch',
	url: 'https://quicknotch.app',
	/**
	 * Where the installers live, for people who already own a licence. This is
	 * the one place to configure the download destination — nothing else on the
	 * site links off to a code host, so that it never implies the product's
	 * source is available.
	 *
	 * TODO before launch: publish this page. Absolute rather than a relative
	 * `/download` so the prerenderer does not try to crawl it and fail the build.
	 */
	downloadUrl: 'https://quicknotch.app/download',
	/**
	 * Checkout. Every "buy" button on the site points here, and the price below
	 * is the single source of truth for what it costs, so the button, the card
	 * and the structured data can never disagree.
	 *
	 * TODO before launch: point this at the live checkout (a hosted cart page or
	 * a payment processor's checkout link).
	 */
	purchaseUrl: 'https://quicknotch.app/buy',
	version: '1.0.0',
	tagline: 'One tiny notch. Apps, system, weather and more.',
	description:
		'QuickNotch is a floating notch and app launcher for Windows and macOS. Pin your apps, watch CPU, GPU, memory, storage and network as live rings, check the weather and the day ahead, and control your music — all at a glance. Start a fullscreen game and the notch turns into a live performance HUD. Style it as an AMOLED notch, a floating pill or a slim drag handle.',
	shortDescription:
		'A floating notch and app dock that shows your apps, system stats, weather, calendar and live controls at a glance on Windows and macOS.',
	author: 'StarkDroid',
	keywords: [
		'app launcher',
		'floating dock',
		'Floating notch for desktop',
		'MacBook notch alternative',
		'macOS app launcher',
		'Windows app launcher',
		'cross-platform app launcher',
		'dock replacement',
		'notch app launcher',
		'quick launcher',
		'app dock',
		'widgets',
		'weather widget',
		'system monitor',
		'CPU and memory monitor',
		'calendar widget',
		'live activities',
		'now playing widget',
		'gaming HUD',
		'FPS counter',
		'in-game overlay',
		'performance monitor',
		'notch themes',
		'productivity utility'
	]
} as const;

/**
 * Commerce. One payment, both installers, every widget — the whole site reads
 * the price from here, including the structured data in `Seo.svelte`.
 */
export const pricing = {
	/** Kept separate from the display label for schema.org's numeric field. */
	amount: '9',
	label: '$9',
	currency: 'USD',
	unit: 'one-time',
	eyebrow: 'The price',
	title: 'One tiny notch. One small price.',
	body: 'Pay once and the notch is yours on both platforms: every widget page, all three looks, and the updates that follow. No subscription, no in-app purchases, no account.',
	/** What the payment actually buys — rendered beside the price. */
	includes: [
		'Windows and macOS installers on one licence',
		'Quick launch, system, weather, calendar and live media',
		'All three notch styles and every surface finish',
		'Updates included for as long as the app ships'
	],
	/** Receipt-footer line under the buy button. */
	finePrint: 'Instant download · Both platforms · No subscription'
} as const;

/** One label for every buy button, so the price is written exactly once. */
export const buyCta = `Buy now — ${pricing.label}` as const;

export const nav = [
	{ label: 'Tour', href: '#tour' },
	{ label: 'Look', href: '#look' },
	{ label: 'Pricing', href: '#pricing' },
	{ label: 'FAQ', href: '#faq' }
] as const;

export const platforms = [
	{
		id: 'windows',
		name: 'Windows',
		requirement: 'Windows 10 & 11 · x64',
		note: '.msi or .exe installer',
		icon: 'windows'
	},
	{
		id: 'macos',
		name: 'macOS',
		requirement: 'macOS 12+ · Apple Silicon & Intel',
		note: '.dmg disk image',
		icon: 'apple'
	}
] as const;

export const hero = {
	// Sticker ribbon that sits on top of the headline.
	badge: 'a notchful of good stuff · pay once',
	// Rendered as separate masked lines so each can animate on its own.
	headlineLines: ['Everything you', 'need. One', 'tiny notch.'],
	sub: 'Pin your apps, watch the machine, check the sky and your day, and boss your music around — from one slim notch at the top of the screen. No windows to arrange, no tab to find.',
	secondaryCta: 'Take the tour',
	trust: ['One-time purchase', 'No account', 'No telemetry', 'Runs offline', 'Windows & macOS']
} as const;

/** Indexable facts. Not rendered: the tour and the look band already say them. */
export const facts = [
	{ label: 'Widget pages', value: '5' },
	{ label: 'Notch styles', value: '3' },
	{ label: 'Platforms on one licence', value: '2' }
] as const;

/**
 * The scroll tour: the one place every widget is explained.
 *
 * Seven states, in the order you meet them — the resting pill, then each page
 * of the wheel, then the gaming HUD that replaces the pill. Copy is deliberately
 * one line and one sentence: the animation carries the rest, and the whole thing
 * replaces what used to be four separate sections.
 */
export type TourStepId =
	| 'quickview'
	| 'apps'
	| 'system'
	| 'weather'
	| 'calendar'
	| 'music'
	| 'gaming';

export const tour = {
	eyebrow: 'The tour',
	title: 'One notch. The whole desktop.',
	lead: 'Hover for your apps. Scroll for the machine, the sky, your day and your music. One shape, seven jobs — and nothing new to learn.',
	steps: [
		{
			id: 'quickview',
			label: 'Quickview',
			title: 'The day, in a sliver.',
			body: 'Date, time, weather, battery and notifications, hugging the top edge. Nothing to dismiss.'
		},
		{
			id: 'apps',
			label: 'Dock',
			title: 'Hover. Your apps arrive.',
			body: 'The pill grows into a dock of pinned apps that lean toward the cursor. One click launches.'
		},
		{
			id: 'system',
			label: 'System',
			title: 'Six rings for the machine.',
			body: 'CPU, GPU, memory, storage, download and upload. Click the page for Task Manager or Activity Monitor.'
		},
		{
			id: 'weather',
			label: 'Weather',
			title: 'The week, without a tab.',
			body: 'Today plus a seven-day outlook, automatic by location or pinned to your own town.'
		},
		{
			id: 'calendar',
			label: 'Calendar',
			title: 'Your day, one glance.',
			body: 'Google Calendar on the page, and the next meeting ticking along in the pill.'
		},
		{
			id: 'music',
			label: 'Music',
			title: 'Now playing, hands on it.',
			body: 'Cover, progress and transport on the page; a spinning disc in the pill while it plays.'
		},
		{
			id: 'gaming',
			label: 'Gaming',
			title: 'Game on. Numbers up.',
			body: 'A fullscreen game swaps the pill for FPS, CPU, GPU, ping and download. Nothing to launch.'
		}
	]
} as const;

export type ThemeId = 'amoled' | 'pill' | 'handle';

export const themes: { id: ThemeId; label: string; blurb: string }[] = [
	{ id: 'amoled', label: 'AMOLED notch', blurb: 'Deep black, flush with the edge. There only when you look.' },
	{ id: 'pill', label: 'Floating pill', blurb: 'A plump little capsule, hovering just off the edge.' },
	{ id: 'handle', label: 'Drag handle', blurb: 'A slim, quiet grip. Barely any notice.' }
];

/** Solid ink or frosted glass — the second axis of "how it looks". */
export type SurfaceId = 'solid' | 'frost';

export const surfaces: { id: SurfaceId; label: string; blurb: string }[] = [
	{
		id: 'solid',
		label: 'Solid',
		blurb: 'Flat, opaque ink. Nothing shows through.'
	},
	{
		id: 'frost',
		label: 'Frosted glass',
		blurb: 'The desktop blurs through, so the wallpaper tints it — on Windows too.'
	}
];

/** The compact "look" band that replaced the three theme cards. */
export const look = {
	eyebrow: 'The look',
	title: 'Three shapes. Two finishes.',
	lead: 'Swap the profile and the surface any time from Settings. Every page keeps its layout.'
} as const;

export const download = {
	title: 'Slap a notch on your desktop',
	body: 'Already own a licence? Grab the installer for the machine you are on, run it once, and boom: a tiny superpower notch. The same licence covers the other platform too, so install it there whenever you like.',
	notes: [
		'Installs on Windows 10 & 11 and macOS 12 or newer.',
		'One licence, both installers — no per-platform upgrade.',
		'Runs offline. The app never phones home.'
	]
} as const;

export const faq = [
	{
		question: 'How much does QuickNotch cost?',
		answer:
			`${pricing.label}, once. No subscription, no in-app purchases, no account. The one payment covers Windows and macOS, and updates are included.`
	},
	{
		question: 'Does QuickNotch work on Windows and macOS?',
		answer:
			'Yes — Windows 10 & 11 and macOS 12 or newer, on Apple silicon and Intel. The same app, the same notch, on both, on one licence.'
	},
	{
		question: 'Does QuickNotch collect or send my data?',
		answer:
			'No telemetry, no analytics, no cloud. It runs offline and never phones home; your pinned apps and settings stay in a local file.'
	},
	{
		question: 'Can I pin any app?',
		answer:
			'Any installed app. Search the list or browse for a bundle or executable on disk, then reorder from Settings.'
	},
	{
		question: 'Does QuickNotch show FPS while I game?',
		answer:
			'On Windows, yes: a fullscreen game turns the pill into a HUD with frame rate, CPU and GPU load, latency and download rate, via Intel PresentMon. macOS shows load and latency only — no supported API exposes another app\u2019s frame rate. Turn the HUD off in Settings if you would rather keep the clock.'
	},
	{
		question: 'Will the notch get in the way?',
		answer:
			'It stays a slim pill at the top-centre of the screen and only grows when you hover it. You can keep it always on top, or have it collapse the moment an app launches — Escape closes it too.'
	},
	{
		question: 'Does QuickNotch start automatically?',
		answer:
			'Toggle "start at login" in Settings and it will be waiting on the edge after every restart.'
	}
] as const;

/** Abstract dock tiles — original artwork, not third-party app icons. */
export type Tile = {
	id: string;
	name: string;
	glyph:
		| 'folder'
		| 'terminal'
		| 'globe'
		| 'code'
		| 'music'
		| 'palette'
		| 'chat'
		| 'camera'
		| 'calc'
		| 'mail';
	from: string;
	to: string;
};

export const dockTiles: Tile[] = [
	{ id: 'files', name: 'Files', glyph: 'folder', from: '#60a5fa', to: '#2563eb' },
	{ id: 'terminal', name: 'Terminal', glyph: 'terminal', from: '#34d399', to: '#059669' },
	{ id: 'browser', name: 'Browser', glyph: 'globe', from: '#818cf8', to: '#4f46e5' },
	{ id: 'editor', name: 'Editor', glyph: 'code', from: '#38bdf8', to: '#0284c7' },
	{ id: 'music', name: 'Music', glyph: 'music', from: '#f472b6', to: '#db2777' },
	{ id: 'design', name: 'Design', glyph: 'palette', from: '#a855f7', to: '#7e22ce' },
	{ id: 'chat', name: 'Chat', glyph: 'chat', from: '#fbbf24', to: '#d97706' },
	{ id: 'camera', name: 'Camera', glyph: 'camera', from: '#f87171', to: '#dc2626' },
	{ id: 'utility', name: 'Utility', glyph: 'calc', from: '#94a3b8', to: '#475569' },
	{ id: 'mail', name: 'Mail', glyph: 'mail', from: '#22d3ee', to: '#0891b2' }
];

/**
 * Google Calendar auth lives on the website now — the consent UI is a page in
 * this theme, not a settings row. What follows is everything a page needs to
 * run the flow. The token exchange itself deliberately stays out of the site:
 * it is a static host, Google requires a client_secret from every client type,
 * and the secret ships inside the desktop app and never leaves it.
 *
 * The flow: the desktop opens {@code connectUrl} with the loopback intent
 * baked into `state` (which carries the loopback port), the connect page sends
 * the user to Google with that exact `state`, Google bounces the browser back
 * to {@code callbackUrl}, and that page performs a plain top-level navigation
 * to the app's `http://127.0.0.1:<port>/oauth?...` — where the code is
 * exchanged. Both {@code callbackUrl} and the connect page's client id must
 * be present in the Google OAuth client's authorized redirect URIs.
 */
export const calendarAuth = {
	authEndpoint: 'https://accounts.google.com/o/oauth2/v2/auth',
	// Web application client (not Desktop: that type cannot register an https
	// redirect URI at all). Must stay in step with CLIENT_ID in
	// GoogleCalendar.kt — the two are the same credential described twice.
	clientId: '317663226214-1cijquso00ocuj44pldu192bgg8jgqio.apps.googleusercontent.com',
	// Not used to build the authorize URL: the connect page prefers the
	// redirect_uri the desktop sends it, falling back to its own origin. That is
	// what makes the flow testable before the site is deployed -- Google's
	// redirect_uri must be the exact URI the app will exchange against, and
	// during local testing both are `vite dev`, not a constant that can live in
	// two places and drift. Kept here for documentation of the production URI
	// that must be registered on the client at go-live.
	callbackUrl: 'https://quicknotch.app/oauth/callback',
	scope:
		'https://www.googleapis.com/auth/calendar.readonly https://www.googleapis.com/auth/userinfo.email'
} as const;
