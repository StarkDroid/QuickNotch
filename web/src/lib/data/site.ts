/**
 * Single source of truth for every string, link and structured-data value on
 * the site. Keeping content out of components makes the copy easy to audit for
 * SEO (crawlable text is prerendered from here) and trivial to update.
 */

export const site = {
	name: 'QuickNotch',
	url: 'https://quicknotch.app',
	license: 'MIT',
	tagline: 'One tiny notch. Apps, system, weather and more.',
	description:
		'QuickNotch is a free, open-source floating notch and app launcher for Windows and macOS. Pin your apps, watch CPU, GPU, memory, storage and network as live rings, check the weather and the day ahead, and control your music — all at a glance. On Windows, start a fullscreen game and the notch turns into a live performance HUD. Style it as an AMOLED notch, a floating pill or a slim drag handle, and let it update itself as new widgets land.',
	shortDescription:
		'A free, open-source floating notch and app dock that shows your apps, system stats, weather, calendar and live controls at a glance on Windows and macOS.',
	author: 'VelocityApps',
	/**
	 * Public addresses. The app carries the repository and Ko-fi links in
	 * `ProjectLinks.kt`; everything else on this site is derived from here.
	 */
	repository: 'https://github.com/StarkDroid/QuickNotch',
	releasesUrl: 'https://github.com/StarkDroid/QuickNotch/releases',
	issuesUrl: 'https://github.com/StarkDroid/QuickNotch/issues',
	kofi: 'https://ko-fi.com/trishiraj',
	/** Absolute routes, for the schema graph and for links inside the copy. */
	downloadPath: '/download',
	changelogPath: '/changelog',
	downloadUrl: 'https://quicknotch.app/download',
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
		'open source app launcher',
		'free app launcher',
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
 * The download band. There is no price to state: QuickNotch is free and
 * MIT-licensed, so the band sells the download rather than a purchase, and the
 * structured data in `Seo.svelte` reads the zero from here.
 */
export const offer = {
	eyebrow: 'The download',
	title: 'Free. Open source. Yours.',
	body: 'Two installers, every widget, the whole source. No licence key, no account, no trial that expires — QuickNotch is MIT licensed, so you can read it, fork it and build your own.',
	price: { amount: '0', label: 'Free', currency: 'USD', unit: 'forever' },
	/** What the download actually contains — rendered beside the price. */
	includes: [
		'Windows and macOS installers, one per platform',
		'Quick launch, system, weather, calendar and live media',
		'All three notch styles and every surface finish',
		'Self-updating releases — new widgets land for free',
		'MIT licensed source: fork it, patch it, ship it'
	],
	finePrint: 'Free forever · No account · No telemetry · MIT licence',
	secondary: 'Read the source'
} as const;

/** One label for every download button on the site. */
export const downloadCta = 'Download QuickNotch' as const;

/** Copy for `/download`: the platform cards, install notes and the tip jar. */
export const download = {
	eyebrow: 'Get QuickNotch',
	title: 'Pick your machine.',
	lead: 'Every release ships one Windows installer and two macOS disk images — Apple silicon and Intel. The buttons below always point at the newest release on GitHub.',
	versionLabel: 'Latest release',
	allReleases: 'All releases on GitHub',
	unsigned:
		'The installers are not code-signed yet, so Windows SmartScreen and macOS Gatekeeper will ask once. On Windows: More info → Run anyway. On macOS: right-click QuickNotch → Open.',
	support: {
		eyebrow: 'One more thing',
		title: 'It is free. It stays free.',
		body: 'QuickNotch is a nights-and-weekends project with no ads, no telemetry and nothing to upsell. If it earned a place on your screen, a coffee keeps the updates coming — entirely optional, and it unlocks nothing.',
		cta: 'Buy me a coffee',
		dismiss: 'No thanks'
	}
} as const;

export const platforms = [
	{
		id: 'windows',
		name: 'Windows',
		requirement: 'Windows 10 & 11 · x64',
		note: 'Installer (.msi), or the .exe if you prefer',
		icon: 'windows',
		blurb:
			'Installs for every user on the machine, adds QuickNotch to the Start menu and lets it update itself in place.'
	},
	{
		id: 'macos',
		name: 'macOS',
		requirement: 'macOS 12+ · Apple silicon & Intel',
		note: 'Disk image (.dmg), one per architecture',
		icon: 'apple',
		blurb:
			'Open the .dmg and drag QuickNotch to Applications. Grab the build that matches your Mac — the app updates itself from there.'
	}
] as const;

export const hero = {
	// Sticker ribbon that sits on top of the headline.
	badge: 'a notchful of good stuff · free & open source',
	// Rendered as separate masked lines so each can animate on its own.
	headlineLines: ['Everything you', 'need. One', 'tiny notch.'],
	sub: 'Pin your apps, watch the machine, check the sky and your day, and boss your music around — from one slim notch at the top of the screen. No windows to arrange, no tab to find.',
	secondaryCta: 'Take the tour',
	trust: ['Free & open source', 'No account', 'No telemetry', 'Runs offline', 'Windows & macOS']
} as const;

/** Indexable facts. Not rendered: the tour and the look band already say them. */
export const facts = [
	{ label: 'Widget pages', value: '5' },
	{ label: 'Notch styles', value: '3' },
	{ label: 'Platforms', value: '2' },
	{ label: 'Licence', value: 'MIT' }
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
			body: 'On Windows, a fullscreen game swaps the pill for FPS, CPU, GPU, ping and download — pick which numbers show in Settings. Nothing to launch.'
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

export const faq = [
	{
		question: 'Is QuickNotch really free?',
		answer:
			'Yes — free and MIT licensed. No price, no account, no trial, no in-app purchases, and no ads. If it earns a place on your screen you can buy me a coffee, but nothing in the app ever asks you to.'
	},
	{
		question: 'Does QuickNotch work on Windows and macOS?',
		answer:
			'Yes — Windows 10 & 11 and macOS 12 or newer, on Apple silicon and Intel. The same app, the same notch, on both.'
	},
	{
		question: 'Can I read or build the source?',
		answer:
			'It is all on GitHub under the MIT licence. The app is Kotlin with Compose Multiplatform for the desktop: clone the repository, run ./gradlew :composeApp:run to launch it, or packageDistributionForCurrentOS to build the installer for your machine.'
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
			'On Windows, yes: a borderless-fullscreen game turns the pill into a HUD with frame rate, CPU and GPU load, latency and download rate, via Intel PresentMon. Exclusive fullscreen bypasses the desktop compositor, so the HUD can\u2019t overlay it — switch the game to borderless fullscreen. The HUD is Windows-only: macOS exposes no supported API for another app\u2019s frame rate, so there is no gaming HUD there. Turn it off in Settings if you would rather keep the clock.'
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
	},
	{
		question: 'How do updates work?',
		answer:
			'QuickNotch checks GitHub in the background, downloads the installer for your machine, and swaps it in after you press "Restart & update" — no download hunt, no uninstall step. Settings → Changelog lists what changed in every version up to the one you are running, and the same history is published on this site.'
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
