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
	{ label: 'Widgets', href: '#widgets' },
	{ label: 'Gaming', href: '#gaming' },
	{ label: 'Themes', href: '#themes' },
	{ label: 'Launch', href: '#launch' },
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
	sub: 'A notch that lives at the top of your screen, always ready. Pin your apps, watch the machine — CPU, GPU, memory, storage and network as live rings — check the weather and the day ahead, and boss your music around, without ever leaving the window you are in.',
	secondaryCta: 'See how it works',
	trust: ['One-time purchase', 'No account', 'No telemetry', 'Runs offline', 'Windows & macOS']
} as const;

export const stats = [
	{
		value: '5',
		label: 'Widgets, always up top',
		detail: 'Apps, system rings, weather, calendar and live media'
	},
	{ value: '3', label: 'Looks, all yours', detail: 'AMOLED notch, floating pill or drag handle' },
	{ value: '2', label: 'Platforms, one licence', detail: 'Buy once, install on Windows and macOS' },
	{ value: '1', label: 'Click and you are in', detail: 'Open any pinned app in an instant' }
] as const;

export type WidgetId = 'apps' | 'system' | 'weather' | 'calendar' | 'music';

export const widgets: { id: WidgetId; label: string; blurb: string }[] = [
	{ id: 'apps', label: 'Quick launch', blurb: 'Your pinned apps, one sunny click away.' },
	{
		id: 'system',
		label: 'System rings',
		blurb: 'CPU, GPU, memory, storage and network as rings. Click through to Task Manager or Activity Monitor.'
	},
	{ id: 'weather', label: 'Weather', blurb: 'Today plus a seven-day outlook. Peek outside without standing up.' },
	{ id: 'calendar', label: 'Calendar', blurb: 'The day ahead from Google Calendar, no tab required.' },
	{ id: 'music', label: 'Live activities', blurb: 'Now playing, with the important buttons.' }
];

/**
 * The gaming quickview.
 *
 * Not a wheel page and not a widget you switch on: while a fullscreen game owns
 * the screen, the collapsed pill itself is replaced by this readout. It is
 * modelled here rather than in `widgets` so the widget count, the pager and the
 * structured data keep describing the actual page stack.
 */
export const gaming = {
	eyebrow: 'Gaming HUD',
	title: 'Your pill becomes a performance readout.',
	body: 'Start a fullscreen game and the collapsed notch trades its clock or album art for live numbers: frames per second, CPU and GPU load, latency and download rate. Nothing to launch, nothing to configure — and nothing covering the game, because the notch already lives on the top edge of the screen. Hover still opens the dock, exactly as it always does.',
	cells: [
		{ label: 'FPS', value: '144' },
		{ label: 'CPU', value: '38%' },
		{ label: 'GPU', value: '91%' },
		{ label: 'PING', value: '12 ms' },
		{ label: 'NET', value: '1.4M/s' }
	],
	modes: [
		{
			id: 'body',
			label: 'Solid body',
			blurb: 'The HUD sits on whatever surface you already picked — ink or frosted glass.'
		},
		{
			id: 'clear',
			label: 'Transparent in games',
			blurb: 'One toggle drops the body and keeps a thin rim plus a soft shadow, so the numbers read over busy scenery.'
		}
	],
	notes: [
		{
			title: 'Detected, not configured',
			body: 'A fullscreen window is the trigger — there is no game list to keep up to date. Close the game and the pill is back.'
		},
		{
			title: 'FPS on Windows, load everywhere',
			body: "Frame rate comes from Intel PresentMon on Windows. macOS shows load and latency instead, because no supported API exposes another app's frame rate."
		},
		{
			title: 'A readout, never a target',
			body: 'The strip has no buttons: bringing the cursor up expands the dock as usual, so nothing is accidentally clicked mid-match.'
		}
	]
} as const;

export type ThemeId = 'amoled' | 'pill' | 'handle';

export const themes: { id: ThemeId; label: string; blurb: string }[] = [
	{ id: 'amoled', label: 'AMOLED notch', blurb: 'Deep black, vanishes into the screen edge. There only when you look.' },
	{ id: 'pill', label: 'Floating pill', blurb: 'A plump little capsule, hovering just off the edge of forever.' },
	{ id: 'handle', label: 'Drag handle', blurb: 'A slim, quiet grip. Very chill. Barely any notice.' }
];

/** Solid ink or frosted glass — the second axis of "how it looks". */
export type SurfaceId = 'solid' | 'frost';

export const surfaces: { id: SurfaceId; label: string; blurb: string }[] = [
	{
		id: 'solid',
		label: 'Solid',
		blurb: 'Flat, opaque ink. Nothing shows through, nothing distracts.'
	},
	{
		id: 'frost',
		label: 'Frosted glass',
		blurb: 'The desktop blurs through the island, so your wallpaper tints it. On Windows as well as macOS.'
	}
];

export const launch = {
	eyebrow: 'The notch overlay',
	title: 'Hover the notch. Get what you need.',
	body: 'The notch rests slim at the top-centre of your screen. Hover it and the overlay answers with an app dock that grows straight out of the notch, right where you are looking. Move along and it tucks itself away, leaving a pixel-wide nothing behind.',
	items: [
		{
			title: 'Hover to expand',
			body: 'Rest over the notch and it grows into an app dock. No clicks, no menu, no fumbling.'
		},
		{
			title: 'Always on top',
			body: 'Keep the notch above everything, or let it politely recede when you want a clean canvas.'
		},
		{
			title: 'Tucks away on its own',
			body: 'Move along and the overlay collapses back to a slim notch. Nothing lingers, nothing gets in your way, and a single press of Escape closes it too.'
		}
	]
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
			`${pricing.label}, once. No subscription, no in-app purchases, no account to create. The one payment covers Windows and macOS, and updates are included.`
	},
	{
		question: 'Does QuickNotch work on Windows and macOS?',
		answer:
			'It runs on Windows 10 & 11 and macOS 12 or newer, on both Apple silicon and Intel. The same app, the same notch, on both platforms — and one licence covers both.'
	},
	{
		question: 'Does QuickNotch collect or send my data?',
		answer:
			'No telemetry, no analytics, no cloud. QuickNotch runs entirely offline and never phones home. Your pinned apps and settings stay in a local file on your machine.'
	},
	{
		question: 'Can I pin any app?',
		answer:
			'Any app that is installed. Search your installed apps, or browse for an app bundle or executable on disk. Reorder and remove them any time from Settings.'
	},
	{
		question: 'Will the notch get in the way?',
		answer:
			'That is the point: it should not get in the way. It stays as a slim pill at the top-centre of your screen and only expands into a dock when you hover over it. You can set it to always stay on top, or collapse it the moment you launch an app.'
	},
	{
		question: 'Can I change how the notch looks?',
		answer:
			'Yes. Pick between an AMOLED notch, a floating pill or a slim drag handle, and tune the magnification, icon size, geometry and whether labels show.'
	},
	{
		question: 'Does QuickNotch start automatically?',
		answer:
			'If you want it to. Toggle "start at login" in Settings and QuickNotch will be waiting on your desktop edge after every restart.'
	},
	{
		question: 'Does QuickNotch show FPS while I game?',
		answer:
			'Yes, on Windows. When a fullscreen game takes the screen the collapsed notch turns into a gaming HUD with frame rate, CPU and GPU load, latency and download rate. The frame rate comes from Intel PresentMon. macOS shows load and latency only, because macOS exposes no supported API for another app\u2019s frame rate. Turn the HUD off in Settings if you would rather keep the clock.'
	},
	{
		question: 'Does the gaming HUD get in the way of the game?',
		answer:
			'No. It replaces the collapsed pill at the very top of the screen, which is already outside the game\u2019s content, and it has no buttons — moving the cursor up expands the dock exactly as before. If you want even less on screen, "transparent in games" drops the notch body and leaves just the numbers, a thin rim and a soft shadow.'
	},
	{
		question: 'Is QuickNotch more than an app launcher?',
		answer:
			'It is. Beyond quick launch there are live system rings (CPU, GPU, memory, storage, upload and download), a seven-day weather outlook, your Google Calendar day ahead and now-playing controls — plus volume, battery and notification indicators in the pill itself.'
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
