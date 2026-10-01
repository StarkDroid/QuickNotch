/**
 * Single source of truth for every string, link and structured-data value on
 * the site. Keeping content out of components makes the copy easy to audit for
 * SEO (crawlable text is prerendered from here) and trivial to update.
 */

export const site = {
	name: 'QuickNotch',
	url: 'https://quicknotch.app',
	/**
	 * Where the installers live. This is the one place to configure the download
	 * destination — nothing else on the site links off to a code host, so that it
	 * never implies the product's source is available.
	 *
	 * TODO before launch: publish this page. Absolute rather than a relative
	 * `/download` so the prerenderer does not try to crawl it and fail the build.
	 */
	downloadUrl: 'https://quicknotch.app/download',
	version: '1.0.0',
	price: '0',
	currency: 'USD',
	tagline: 'One tiny notch. Apps, weather, stats and more.',
	description:
		'QuickNotch is a free Dynamic Island-style notch and app launcher dock for Windows and macOS. Pin your apps, check the weather, system usage, game FPS and live controls at a glance — styled as an AMOLED notch, a floating pill or a slim drag handle. The widget stack keeps growing, so there is always a new reason to glance up.',
	shortDescription:
		'A free Dynamic Island-style notch and app dock that shows your apps, weather, stats and live controls at a glance on Windows and macOS.',
	author: 'StarkDroid',
	keywords: [
		'app launcher',
		'floating dock',
		'Dynamic Island for desktop',
		'MacBook notch alternative',
		'macOS app launcher',
		'Windows app launcher',
		'cross-platform app launcher',
		'dock replacement',
		'notch app launcher',
		'quick launcher',
		'free app dock',
		'widgets',
		'weather widget',
		'system monitor',
		'fps counter',
		'live activities',
		'now playing widget',
		'notch themes',
		'productivity utility'
	]
} as const;

export const nav = [
	{ label: 'Widgets', href: '#widgets' },
	{ label: 'Themes', href: '#themes' },
	{ label: 'Launch', href: '#launch' },
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
	badge: 'a notchful of good stuff · 100% free',
	// Rendered as separate masked lines so each can animate on its own.
	headlineLines: ['Everything you', 'need. One', 'tiny notch.'],
	sub: 'A Dynamic Island-style notch that lives at the top of your screen, always ready. Pin your apps, peek at the weather, watch your FPS and boss your music around — without ever leaving the window you are in. And it keeps growing: more widgets and tricks land with every release, so there is always a fresh reason to glance up.',
	primaryCta: 'Download for free',
	secondaryCta: 'See how it works',
	trust: ['No account', 'No telemetry', 'Runs offline', 'Windows & macOS']
} as const;

export const stats = [
	{
		value: '5',
		label: 'Widgets, always up top',
		detail: 'Apps, weather, stats, FPS and live controls'
	},
	{ value: '3', label: 'Looks — all yours', detail: 'AMOLED notch, floating pill or drag handle' },
	{ value: '2', label: 'Platforms, one app', detail: 'Same vibe on Windows and macOS' },
	{ value: '1', label: 'Click and you are in', detail: 'Open any pinned app in an instant' }
] as const;

export type WidgetId = 'apps' | 'weather' | 'system' | 'fps' | 'music';

export const widgets: { id: WidgetId; label: string; blurb: string }[] = [
	{ id: 'apps', label: 'Quick launch', blurb: 'Your pinned apps, one sunny click away.' },
	{ id: 'weather', label: 'Weather', blurb: 'Peek outside without standing up.' },
	{ id: 'system', label: 'System usage', blurb: 'CPU, RAM, GPU and network, live and honest.' },
	{ id: 'fps', label: 'Game mode', blurb: 'A live FPS counter. Your framerate has a fan club now.' },
	{ id: 'music', label: 'Live activities', blurb: 'Now playing — with the important buttons.' }
];

export type ThemeId = 'amoled' | 'pill' | 'handle';

export const themes: { id: ThemeId; label: string; blurb: string }[] = [
	{ id: 'amoled', label: 'AMOLED notch', blurb: 'Deep black, vanishes into the screen edge. There only when you look.' },
	{ id: 'pill', label: 'Floating pill', blurb: 'A plump little capsule, hovering just off the edge of forever.' },
	{ id: 'handle', label: 'Drag handle', blurb: 'A slim, quiet grip. Very chill. Barely any notice.' }
];

export const launch = {
	eyebrow: 'The notch overlay',
	title: 'Hover the notch. Get what you need.',
	body: 'The notch rests slim at the top-centre of your screen. Hover it and the overlay answers — an app dock that grows straight out of the notch, right where you are looking. Move along and it tucks itself away, leaving a pixel-wide nothing behind.',
	items: [
		{
			title: 'Hover to expand',
			body: 'Rest over the notch and it grows into an app dock, exactly the way a Dynamic Island does — no clicks, no menu, no fumbling.'
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
	body: 'Grab the installer for your platform, run it once, and boom — a tiny superpower notch. Apps, weather, stats and media controls on day one, with new widgets queued up after. No account, no credit card, no signing your soul away.',
	notes: [
		'Installs on Windows 10 & 11 and macOS 12 or newer.',
		'No account, no telemetry, no cloud.',
		'Runs offline — the app never phones home.'
	]
} as const;

export const faq = [
	{
		question: 'Is QuickNotch really free?',
		answer:
			'Yes — completely. There is no trial, no in-app purchase and no account to create. Download the installer, launch it once, and it just works.'
	},
	{
		question: 'Does QuickNotch work on Windows and macOS?',
		answer:
			'It runs on Windows 10 & 11 and macOS 12 or newer, on both Apple silicon and Intel. The same app, the same notch, on both platforms.'
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
			'That is the point — it should not. It stays as a slim pill at the top-centre of your screen and only expands into a dock when you hover over it. You can set it to always stay on top, or collapse it the moment you launch an app.'
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
		question: 'Is QuickNotch more than an app launcher?',
		answer:
			'It started as a launcher, and the notch has grown into a whole widget stack: apps, weather, system usage, game FPS and live media controls today, with more widgets and tricks queued up for the next updates. Every bit of it is free, offline-facing and one glance away.'
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
	clientId:
		'317663226214-ojbngp1n0p81tttll9sjq9ou74mvjegk.apps.googleusercontent.com',
	callbackUrl: 'https://quicknotch.app/oauth/callback',
	scope:
		'https://www.googleapis.com/auth/calendar.readonly https://www.googleapis.com/auth/userinfo.email'
} as const;
