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
	tagline: 'One notch. Everything you need.',
	description:
		'QuickNotch is a glanceable notch for Windows and macOS. Scroll through quick-launch apps, weather, system usage, a game FPS counter and live activity controls — styled as an AMOLED notch, a floating pill or a slim drag handle.',
	shortDescription:
		'A glanceable notch that shows your apps, weather, stats and live controls at a glance.',
	author: 'StarkDroid',
	keywords: [
		'app launcher',
		'floating dock',
		'Dynamic Island for desktop',
		'macOS app launcher',
		'Windows app launcher',
		'cross-platform app launcher',
		'dock replacement',
		'notch app launcher',
		'quick launcher',
		'widgets',
		'weather widget',
		'system monitor',
		'fps counter',
		'live activities',
		'notch themes',
		'productivity utility'
	]
} as const;

export const nav = [
	{ label: 'Widgets', href: '#widgets' },
	{ label: 'Themes', href: '#themes' },
	{ label: 'Launch', href: '#launch' }
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
	// Rendered as separate masked lines so each can animate on its own.
	headlineLines: ['One notch.', 'Everything', 'you need.'],
	sub: 'A glanceable notch that lives on any screen edge. Scroll through your apps, weather, system stats, game FPS and live controls — styled exactly the way you like it.',
	primaryCta: 'Download for free',
	trust: ['No account', 'No telemetry', 'Runs offline', 'Windows & macOS']
} as const;

export const stats = [
	{
		value: '5',
		label: 'Glanceable widgets',
		detail: 'Apps, weather, stats, FPS and live controls'
	},
	{ value: '3', label: 'Notch styles', detail: 'AMOLED notch, floating pill or drag handle' },
	{ value: '2', label: 'Platforms, one app', detail: 'The same look on Windows and macOS' },
	{ value: '1', label: 'Click to launch', detail: 'Open any pinned app in an instant' }
] as const;

export type WidgetId = 'apps' | 'weather' | 'system' | 'fps' | 'music';

export const widgets: { id: WidgetId; label: string; blurb: string }[] = [
	{ id: 'apps', label: 'Quick launch', blurb: 'Your pinned apps, one click away.' },
	{ id: 'weather', label: 'Weather', blurb: 'Conditions and the next few days at a glance.' },
	{ id: 'system', label: 'System usage', blurb: 'CPU, RAM, GPU and network, live.' },
	{ id: 'fps', label: 'Game mode', blurb: 'A live FPS counter while you play.' },
	{ id: 'music', label: 'Live activities', blurb: 'Now playing, with controls.' }
];

export type ThemeId = 'amoled' | 'pill' | 'handle';

export const themes: { id: ThemeId; label: string; blurb: string }[] = [
	{ id: 'amoled', label: 'AMOLED notch', blurb: 'Blends into the screen edge — deep black, there only when you look.' },
	{ id: 'pill', label: 'Floating pill', blurb: 'A soft rounded pill floating just off the edge.' },
	{ id: 'handle', label: 'Drag handle', blurb: 'A slim, quiet handle that stays out of the way.' }
];

export const launch = {
	eyebrow: 'Launch from anywhere',
	title: 'One click, from any app',
	body: 'Because the notch floats above whatever is on screen, you never leave the window you are working in. The dock collapses behind you on launch if you want it to.',
	items: [
		{
			title: 'One click to launch',
			body: 'Every pinned app opens the moment you click, exactly as the system would launch it.'
		},
		{
			title: 'Always on top',
			body: 'Keep the notch above everything, or let it fall behind when you want a clean canvas.'
		},
		{
			title: 'Collapse on launch',
			body: 'Optionally tuck the dock away the moment your app opens, ready for the next one.'
		}
	]
} as const;

export const download = {
	title: 'Put a notch on your desktop',
	body: 'Download the installer for your platform and you are done — the first launch gives you a sensible default pin set to start from.',
	notes: [
		'Installs on Windows 10 & 11 and macOS 12 or newer.',
		'No account, no telemetry, no cloud.',
		'Runs offline — the app never phones home.'
	]
} as const;

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
