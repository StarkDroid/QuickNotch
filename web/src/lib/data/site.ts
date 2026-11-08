/** Single source of truth for every string, link and structured-data value on the site. */

export const site = {
	name: 'QuickNotch',
	url: 'https://quicknotch.app',
	license: 'MIT',
	/** The accessible label on the header and footer marks. */
	homeLabel: 'QuickNotch, home',
	tagline: 'One tiny notch. Apps, system, weather and more.',
	description:
		'QuickNotch is a free, open-source floating notch and app launcher for Windows and macOS. Pin your apps, watch CPU, GPU, memory, storage and network as live rings, check the weather and the day ahead, and control your music, all at a glance. On Windows, start a fullscreen game and the notch turns into a live performance HUD. Style it as an AMOLED notch, a floating pill or a slim drag handle, and let it update itself as new widgets land.',
	shortDescription:
		'A free, open-source floating notch and app dock that shows your apps, system stats, weather, calendar and live controls at a glance on Windows and macOS.',
	author: 'VelocityApps',
	/** Public addresses; the app mirrors the repo and Ko-fi links in `ProjectLinks.kt`. */
	repository: 'https://github.com/StarkDroid/QuickNotch',
	repositoryLabel: 'github.com/StarkDroid/QuickNotch',
	releasesUrl: 'https://github.com/StarkDroid/QuickNotch/releases',
	issuesUrl: 'https://github.com/StarkDroid/QuickNotch/issues',
	kofi: 'https://ko-fi.com/trishiraj',
	/** Absolute routes, for the schema graph and for links inside the copy. */
	homePath: '/',
	downloadPath: '/download',
	changelogPath: '/changelog',
	privacyPath: '/privacy',
	termsPath: '/terms',
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
		'productivity utility',
		'notch app',
		'notch app for Windows',
		'notch app for macOS',
		'Dynamic Island for Windows',
		'MacBook notch app',
		'Windows 11 notch',
		'NotchNook alternative',
		'Boring Notch alternative',
		'free notch app',
		'open source notch app',
		'app dock for Windows',
		'app dock for macOS',
		'menu bar app'
	]
} as const;

/** Cloudflare Web Analytics: cookieless, no cross-site identifier, and the token is public, not a secret. */
export const analytics = {
	src: 'https://static.cloudflareinsights.com/beacon.min.js',
	token: '4465c698879e4120b4c3058ad5f56c71'
} as const;

/** Document-level SEO copy that is not already a page heading. */
export const seo = {
	title: 'QuickNotch: the free notch app and dock for macOS and Windows',
	description:
		'Free, open-source notch app for Windows and macOS. Pin apps, see CPU, weather and calendar, control music, and a Windows gaming HUD. No account or telemetry.',
	alternateName: 'QuickNotch notch app',
	applicationCategory: 'UtilitiesApplication',
	applicationSubCategory: 'Application Launcher',
	organizationDescription: `Makers of ${site.name}, a floating app launcher and notch for Windows and macOS.`,
	featureList: [
		'Five glanceable widgets with vertical-scroll pagination',
		'Quick-launch apps, live system rings, weather, calendar and now-playing controls',
		'System page: CPU, GPU, memory, storage, download and upload rings',
		'Windows-only gaming HUD: FPS, CPU, GPU, latency and download rate while a fullscreen game runs',
		'Transparent in games: the island body and rim drop away, leaving only the metrics',
		'Three notch styles: AMOLED notch, floating pill or drag handle',
		'Solid ink or frosted glass, with the desktop blurring through the island',
		'Pin any installed app or browse for one on disk',
		'Launch from anywhere with always-on-top overlay',
		'Start at login automatically',
		'Runs offline with no account and no telemetry',
		'Free and open source under the MIT licence',
		'Windows and macOS from one download page'
	],
	widgetListName: 'QuickNotch widgets',
	themeListName: 'QuickNotch notch themes',
	ogImageAlt:
		'QuickNotch: a floating notch at the top of a desktop that expands into a dock of pinned apps, with system, weather and calendar widgets, for Windows and macOS.',
	twitterImageAlt:
		'QuickNotch: a floating notch at the top of a desktop that expands into a dock of pinned apps.'
} as const;

/** Labels shared by the chrome: the way home, the skip link, the header button. */
export const chrome = {
	home: 'Home',
	skip: 'Skip to content',
	download: 'Download'
} as const;

/** Prerendered to `404.html`, which Cloudflare serves for unknown addresses, so a mistyped URL is a real 404. */
export const notFound = {
	eyebrow: '404',
	title: 'This page never made it into the dock.',
	lead: 'The address is wrong, or the page moved after it was linked. The tour, the download and the changelog are all exactly where they were.',
	home: 'Back to the homepage',
	download: 'Get the app'
} as const;

/**
 * Public legal pages. Google asks for both before it will verify an OAuth app,
 * and a reader deserves the same links without hunting for them.
 */
export const privacy = {
	seo: {
		title: 'Privacy policy',
		description:
			'What QuickNotch stores on your machine, how Google Calendar access works, which third-party services the app and site contact, and how to remove your data.'
	},
	eyebrow: 'Privacy',
	title: 'What QuickNotch does with your data.',
	lead: 'The app has no account, no telemetry and no server of ours. This page says what stays on your machine, what leaves it, and how to remove it.',
	updated: 'Last updated 8 November 2026',
	sections: [
		{
			id: 'short',
			title: 'The short version',
			paragraphs: [
				'QuickNotch is a desktop app and a static website. There is no sign-up, no analytics SDK in the app, and no server that stores anything about you.',
				'Your settings, pinned apps, caches and Google Calendar tokens stay in the app data folder on your machine. The website counts visits with Cloudflare Web Analytics, which uses no cookies and does not follow you between sites.'
			]
		},
		{
			id: 'storage',
			title: 'What the app stores',
			paragraphs: [
				'QuickNotch keeps its state in a local folder: `%APPDATA%\\QuickNotch` on Windows, `~/Library/Application Support/QuickNotch` on macOS. That folder holds settings, pinned apps, the weather and calendar caches, the downloaded changelog and, if you connect Calendar, the OAuth tokens.',
				'None of it is uploaded. To remove it, quit the app and delete that folder. To remove only the Calendar connection, open Settings, Calendar, Disconnect, then revoke QuickNotch at `myaccount.google.com/permissions` if you want the grant gone from your Google account as well.'
			]
		},
		{
			id: 'google-calendar',
			title: 'Google Calendar, when you connect it',
			paragraphs: [
				'Calendar is optional. If you connect it, QuickNotch asks Google for two scopes: `calendar.readonly` to read your calendar and `userinfo.email` to show which account is connected. It never asks for write access, contacts or Gmail.',
				'The sign-in happens in your browser. The app opens `quicknotch.app/connect`, Google returns the one-time code to `quicknotch.app/oauth/callback`, and that page forwards it to a short-lived loopback server on `127.0.0.1` on your machine. The app exchanges the code directly with Google. The code and the tokens are not sent to us.',
				'Events are read to draw the calendar page and the pill ticker, then cached locally. They are not shared, sold, used for advertising, used to train AI models or read by a person. QuickNotch\'s use and transfer to any other app of information received from Google APIs will adhere to the Google API Services User Data Policy, including the Limited Use requirements.'
			]
		},
		{
			id: 'weather',
			title: 'Weather and location',
			paragraphs: [
				'Weather is keyless. The app asks `ip-api.com` for a city-level location based on your IP address, then asks `open-meteo.com` for the forecast. Those requests leave your machine and include your IP address, as any web request does. No precise location or GPS permission is used.',
				'If you pick a place manually, that place is stored in the local settings file and used instead of the IP lookup.'
			]
		},
		{
			id: 'system',
			title: 'System, media, notifications and games',
			paragraphs: [
				'The System page, the music controls, the gaming HUD and the notification count read data from your own machine and draw it. CPU, GPU, memory, disk and network readings, media metadata and artwork, game detection, frame rate and the macOS Notification Center count are not transmitted anywhere.',
				'The macOS notification count needs Full Disk Access because Apple keeps that store behind it. QuickNotch reads a count, not the contents of individual notifications.'
			]
		},
		{
			id: 'updates',
			title: 'Updates and the changelog',
			paragraphs: [
				'The app checks GitHub for a newer release and downloads the changelog from the release assets. GitHub sees the request and your IP address. There is no account and no telemetry in that request. Installer downloads come from GitHub and are subject to GitHub\'s terms and privacy policy.'
			]
		},
		{
			id: 'website',
			title: 'The website',
			paragraphs: [
				'quicknotch.app is a static site served by Cloudflare. Cloudflare processes technical request data such as your IP address and user agent to deliver the site and protect it, as a hosting provider normally does.',
				'Page counts use Cloudflare Web Analytics. It is cookieless, has no cross-site identifier and needs no consent banner. The `/connect` and `/oauth` pages are deliberately not counted, so the one-time code in the callback URL never reaches an analytics endpoint.',
				'The site loads no third-party fonts or images. The only third-party script is the Cloudflare Web Analytics beacon described above; the Ko-fi button is a local image and nothing is requested from Ko-fi until you click it. The site sets no cookies and has no newsletter or contact form.'
			]
		},
		{
			id: 'never',
			title: 'What we never do',
			paragraphs: [
				'We do not sell or rent personal data, run advertising or profiling, or add telemetry to the app. We do not ask for an account, and there is no cloud sync.',
				'If you report a bug on GitHub, the information you choose to put in that issue is public and is handled under GitHub\'s terms. Do not paste tokens or private calendar details into an issue.'
			]
		},
		{
			id: 'children',
			title: 'Children',
			paragraphs: [
				'QuickNotch is a general-purpose desktop utility and is not directed at children under 13, or under 16 in the EEA and the UK. We do not knowingly collect personal data from children.'
			]
		},
		{
			id: 'changes',
			title: 'Changes and contact',
			paragraphs: [
				'If this policy changes, the date at the top changes with it and the new version is published at this address.',
				'Questions about privacy, or a request to delete something: open an issue at `github.com/StarkDroid/QuickNotch/issues`. QuickNotch is maintained by VelocityApps.'
			]
		}
	]
} as const;

export const terms = {
	seo: {
		title: 'Terms of service',
		description:
			'The terms for using quicknotch.app and downloading QuickNotch, including the MIT licence, third-party services and the limits of the warranty.'
	},
	eyebrow: 'Terms',
	title: 'The terms, in plain language.',
	lead: 'QuickNotch is free software under the MIT License. These terms cover the website and the download; the licence covers the app itself.',
	updated: 'Last updated 8 November 2026',
	sections: [
		{
			id: 'licence',
			title: 'The app is MIT licensed',
			paragraphs: [
				'QuickNotch is released under the MIT License. You may use, copy, modify, merge, publish, distribute, sublicense and sell copies of the software, provided the copyright notice and permission notice stay with it.',
				`The full licence is in the repository at ${site.repository}/blob/main/LICENSE. If these terms and the licence disagree about the software, the licence wins.`
			]
		},
		{
			id: 'website',
			title: 'Using the website',
			paragraphs: [
				'You may read, link to and share the site. Do not try to break it, scrape it at a rate that harms it, or use it to misrepresent an affiliation with QuickNotch.',
				'Links to GitHub, Google, Ko-fi and other services lead to sites with their own terms and privacy policies.'
			]
		},
		{
			id: 'downloads',
			title: 'Downloads and releases',
			paragraphs: [
				'Installers are published on GitHub and are free of charge. The Windows and macOS installers are not code-signed yet, so the operating system will ask for confirmation on first launch. Every release publishes `SHA256SUMS.txt`; check it if you want to verify a download.',
				'Ko-fi is optional. Nothing in the app or on the site is gated behind a payment.'
			]
		},
		{
			id: 'third-parties',
			title: 'Third-party services',
			paragraphs: [
				'QuickNotch can connect to Google Calendar, reads weather from Open-Meteo and ip-api, and checks GitHub for releases. The website is hosted by Cloudflare and counts visits with Cloudflare Web Analytics. The Ko-fi button links to Ko-fi. Each service has its own terms, and they apply when you use it.'
			]
		},
		{
			id: 'warranty',
			title: 'No warranty',
			paragraphs: [
				'QuickNotch and this website are provided as is, without warranty of any kind, express or implied, including merchantability, fitness for a particular purpose and non-infringement. You use the app at your own risk. Keep backups of anything you cannot afford to lose.'
			]
		},
		{
			id: 'liability',
			title: 'Limitation of liability',
			paragraphs: [
				'To the maximum extent permitted by law, VelocityApps and the QuickNotch contributors are not liable for indirect, incidental, special or consequential damages, or for lost data, arising from the use of the app or the site. Nothing here limits liability that cannot be limited by law.'
			]
		},
		{
			id: 'trademarks',
			title: 'Trademarks',
			paragraphs: [
				'Apple, macOS, MacBook, Windows, Google, GitHub, Cloudflare and Ko-fi are trademarks of their respective owners. QuickNotch is not affiliated with or endorsed by any of them.'
			]
		},
		{
			id: 'changes',
			title: 'Changes and contact',
			paragraphs: [
				'These terms may change; the date at the top changes when they do, and the current version stays at this address.',
				'Questions: open an issue at `github.com/StarkDroid/QuickNotch/issues`.'
			]
		}
	]
} as const;

/** The floating support pill, and the artwork it never replaces. */
export const support = {
	fabLabel: 'Support me',
	kofiAlt: 'Buy me a coffee on Ko-fi'
} as const;

/** The footer, which is the same on every page. */
export const footer = {
	linksLabel: 'More',
	links: [
		{ label: chrome.home, href: site.homePath, external: false },
		{ label: 'Download', href: site.downloadPath, external: false },
		{ label: 'Changelog', href: site.changelogPath, external: false },
		{ label: 'Privacy', href: site.privacyPath, external: false },
		{ label: 'Terms', href: site.termsPath, external: false },
		{ label: 'Source', href: site.repository, external: true },
		{ label: 'Issues', href: site.issuesUrl, external: true },
		{ label: 'Back to top', href: '#top', external: false }
	],
	/** The year is passed in at render time, so a prerendered page never goes stale. */
	copyright: (year: number, version: string) =>
		`© ${year} ${site.author} · ${site.license} licensed · v${version}`,
	note: 'printed on cream, obviously'
} as const;

/** The download band sells a free download rather than a purchase; `Seo.svelte` reads the zero price from here. */
export const offer = {
	eyebrow: 'The download',
	title: 'Free. Open source. Yours.',
	body: 'Two installers, every widget, the whole source. No licence key, no account, no trial that expires. QuickNotch is MIT licensed, so you can read it, fork it and build your own.',
	price: { amount: '0', label: 'Free', currency: 'USD', unit: 'forever' },
	/** What the download actually contains, rendered beside the price. */
	includes: [
		'Windows and macOS installers in every release',
		'Quick launch, system, weather, calendar and live media',
		'All three notch styles and every surface finish',
		'Self-updating releases, so new widgets land for free',
		'MIT licensed source: fork it, patch it, ship it'
	],
	finePrint: 'Free forever · No account · No telemetry · MIT licence',
	secondary: 'Read the source'
} as const;

/** The ticket in the download band. */
export const ticket = {
	sticker: 'free forever ★ MIT licensed',
	kicker: 'Free · MIT licence · Windows + macOS',
	lede: 'Download it, read the source, keep it. No licence key, no account, no trial that runs out.',
	includesLabel: 'In the box',
	serial: 'no. 0001 · built in the open · MIT'
} as const;

/** One label for every download button on the site. */
export const downloadCta = 'Download QuickNotch' as const;

/** Copy for `/download`: the platform cards, install notes and the tip jar. */
export const download = {
	seo: {
		title: 'Download QuickNotch for Windows and macOS',
		description:
			'Download QuickNotch for Windows 10 & 11 or macOS 12+, free and open source. Installers for both platforms, self-updating, MIT licensed.'
	},
	eyebrow: 'Get QuickNotch',
	title: 'Pick your machine.',
	lead: 'Every release ships two Windows installers and two macOS disk images, one per Mac architecture. The buttons below always point at the newest release on GitHub.',
	versionLabel: 'Latest release',
	releaseNotes: 'release notes',
	changelogLink: 'changelog',
	allReleases: 'All releases on GitHub',
	unsigned:
		'The installers are not code-signed yet, so Windows SmartScreen and macOS Gatekeeper will ask once. On Windows: More info → Run anyway. On macOS: right-click QuickNotch → Open.',
	checksums: 'Every file is published as a GitHub release, with SHA256SUMS.txt beside it.',
	yourPlatform: 'your platform',
	/** Shown once a download has started; asks for a coffee but gates nothing. */
	thanks: {
		eyebrow: 'Downloading',
		title: 'Thanks for picking QuickNotch.',
		body: 'The installer is on its way. QuickNotch is free, MIT licensed, and there is nothing to sign up for. If it earns a place on your screen, a coffee keeps the updates coming.',
		dismiss: 'Maybe later'
	}
} as const;

export const platforms = [
	{
		id: 'windows',
		name: 'Windows',
		requirement: 'Windows 10 & 11 · x64',
		icon: 'windows',
		blurb:
			'Installs for every user on the machine, adds QuickNotch to the Start menu and lets it update itself in place.'
	},
	{
		id: 'macos',
		name: 'macOS',
		requirement: 'macOS 12+ · Apple silicon & Intel',
		icon: 'apple',
		blurb:
			'Open the .dmg and drag QuickNotch to Applications. Grab the build that matches your Mac. The app updates itself from there.'
	}
] as const;

/** The "Source: …" line both subpages end on. */
export const sourceNote = {
	label: 'Source',
	licensed: 'MIT licensed'
} as const;

export const hero = {
	// Sticker ribbon that sits on top of the headline.
	badge: 'a notchful of good stuff · free & open source',
	// Rendered as separate masked lines so each can animate on its own.
	headlineLines: ['Everything you', 'need. One', 'tiny notch.'],
	sub: 'The floating notch app and app dock for Windows and macOS. Pin your apps, watch the machine, check the sky and your day, and boss your music around, all from one slim notch at the top of the screen. No windows to arrange, no tab to find.',
	secondaryCta: 'Take the tour',
	trust: ['Free & open source', 'No account', 'No telemetry', 'Runs offline', 'Windows & macOS'],
	scrollHint: 'one scroll, the whole tour',
	screenNote: 'hover it: there is the dock'
} as const;

/** Indexable facts. Not rendered: the tour and the look band already say them. */
export const facts = [
	{ label: 'Widget pages', value: '5' },
	{ label: 'Notch styles', value: '3' },
	{ label: 'Platforms', value: '2' },
	{ label: 'Licence', value: 'MIT' }
] as const;

/** The scroll tour: the one place every widget is explained, seven states in the order you meet them. */
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
	lead: 'Hover for your apps. Scroll for the machine, the sky, your day and your music. One shape, seven jobs, and nothing new to learn.',
	hint: 'scroll and the lid follows',
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
			body: 'On Windows, a fullscreen game swaps the pill for FPS, CPU, GPU, ping and download. Pick which numbers show in Settings. Nothing to launch.'
		}
	]
} as const;

export type ThemeId = 'amoled' | 'pill' | 'handle';

export const themes: { id: ThemeId; label: string; blurb: string }[] = [
	{ id: 'amoled', label: 'AMOLED notch', blurb: 'Deep black, flush with the edge. There only when you look.' },
	{ id: 'pill', label: 'Floating pill', blurb: 'A plump little capsule, hovering just off the edge.' },
	{ id: 'handle', label: 'Drag handle', blurb: 'A slim, quiet grip. Barely any notice.' }
];

/** Solid ink or frosted glass: the second axis of "how it looks". */
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
		blurb: 'The desktop blurs through, so the wallpaper tints it. Windows gets this too.'
	}
];

/** The compact look band: shape and finish selectors. */
export const look = {
	eyebrow: 'The look',
	title: 'Three shapes. Two finishes.',
	lead: 'Swap the profile and the surface any time from Settings. Every page keeps its layout.',
	shapeRef: 'shape',
	finishRef: 'finish'
} as const;

export const faqHead = {
	title: 'FAQ',
	lead: 'Straight answers about the things people ask us most.'
} as const;

export const faq = [
	{
		question: 'Is QuickNotch really free?',
		answer:
			'Yes, it is free and MIT licensed. No price, no account, no trial, no in-app purchases, and no ads. If it earns a place on your screen you can buy me a coffee, but nothing in the app ever asks you to.'
	},
	{
		question: 'Does QuickNotch work on Windows and macOS?',
		answer:
			'Yes, on Windows 10 & 11 and macOS 12 or newer, with Apple silicon and Intel builds. The same app, the same notch, on both.'
	},
	{
		question: 'Can I read or build the source?',
		answer:
			'It is all on GitHub under the MIT licence. The app is Kotlin with Compose Multiplatform for the desktop: clone the repository, run ./gradlew :composeApp:run to launch it, or packageDistributionForCurrentOS to build the installer for your machine.'
	},
	{
		question: 'Does QuickNotch collect or send my data?',
		answer:
			'The app has no telemetry, no analytics and no cloud: it runs offline and never phones home, and your pinned apps and settings stay in a local file. This site counts visits with Cloudflare Web Analytics, which is cookieless and does not follow you between sites.'
	},
	{
		question: 'Can I pin any app?',
		answer:
			'Any installed app. Search the list or browse for a bundle or executable on disk, then reorder from Settings.'
	},
	{
		question: 'Does QuickNotch show FPS while I game?',
		answer:
			'On Windows, yes: a borderless-fullscreen game turns the pill into a HUD with frame rate, CPU and GPU load, latency and download rate, via Intel PresentMon. Exclusive fullscreen bypasses the desktop compositor, so the HUD cannot overlay it. Switch the game to borderless fullscreen. The HUD is Windows-only: macOS exposes no supported API for another app\u2019s frame rate, so there is no gaming HUD there. Turn it off in Settings if you would rather keep the clock.'
	},
	{
		question: 'Will the notch get in the way?',
		answer:
			'It stays a slim pill at the top-centre of the screen and only grows when you hover it. You can keep it always on top, or have it collapse the moment an app launches. Escape closes it too.'
	},
	{
		question: 'Does QuickNotch start automatically?',
		answer:
			'Toggle "start at login" in Settings and it will be waiting on the edge after every restart.'
	},
	{
		question: 'How do updates work?',
		answer:
			'QuickNotch checks GitHub in the background, downloads the installer for your machine, and swaps it in after you press "Restart & update", with no download hunt and no uninstall step. Settings → Changelog lists what changed in every version up to the one you are running, and the same history is published on this site.'
	},
	{
		question: 'Is there a notch app for Windows?',
		answer:
			'Windows has no built-in notch or Dynamic Island, so QuickNotch draws its own at the top of the screen: a notch that sits flush with the edge, or a floating pill. It runs on Windows 10 and 11 (x64) and brings the same dock, system rings, weather, calendar and music controls as the Mac version, plus the gaming HUD.'
	},
	{
		question: 'How is QuickNotch different from NotchNook or Boring Notch?',
		answer:
			'NotchNook and Boring Notch are macOS-only; NotchNook is paid and closed source, and Boring Notch is GPL-3.0. QuickNotch is MIT licensed, free, and runs on both Windows and macOS 12 or newer, with a dock, system and weather pages, calendar, music and a Windows gaming HUD. There is no account, subscription or telemetry.'
	},
	{
		question: 'Does QuickNotch work on a MacBook without a notch?',
		answer:
			'Yes. On a MacBook without a notch, choose the floating pill or drag-handle style and QuickNotch sits at the top-centre of the screen. It runs on macOS 12 or newer, on Apple silicon and Intel.'
	},
	{
		question: 'Is QuickNotch a Dynamic Island for Windows?',
		answer:
			'It is the same idea. Windows has no Dynamic Island, so QuickNotch puts a live island at the top of the screen: hover to open the dock, scroll for system, weather, calendar and music, and a borderless-fullscreen game turns it into a performance HUD. Choose the notch shape or a floating pill.'
	},
	{
		question: 'Does QuickNotch replace the Dock or the Windows taskbar?',
		answer:
			'No. The macOS Dock and the Windows taskbar still hold every app. QuickNotch is a shortcut layer for the handful you launch all day, plus glanceable widgets, so it sits above them rather than replacing them.'
	}
] as const;

/** The published history at `/changelog`; `kinds` also drives release group order and labels. */
export const changelogPage = {
	seo: {
		title: 'QuickNotch changelog and release notes',
		description:
			'QuickNotch release notes, newest first: new widgets, fixes and improvements for the Windows and macOS notch, straight from the published release feed.'
	},
	eyebrow: 'The changelog',
	title: 'Every release, in public.',
	lead: 'The same feed the app shows under Settings → Changelog. Newest first, written for people rather than for a diff.',
	empty: 'The first public release is still being published.',
	emptyCta: 'Read it on GitHub',
	current: 'Latest release',
	updated: 'updated',
	releaseLink: 'release page',
	kinds: [
		{ id: 'new', label: 'New' },
		{ id: 'improved', label: 'Improved' },
		{ id: 'fixed', label: 'Fixed' }
	]
} as const;

/** The consent page the desktop opens for Google Calendar. */
export const connectPage = {
	title: 'Connect Google Calendar',
	description:
		'Sign in with Google so QuickNotch can show your calendar. This page is opened by the desktop app, not linked from the site.',
	backToSite: 'Back to site',
	eyebrow: 'Google Calendar · read-only',
	headline: 'Connect QuickNotch',
	headlineHighlight: 'to your calendar',
	lead: "The notch shows today's upcoming events at a glance, and scrolls the next one as a live ticker, without ever needing to open Google Calendar. The connection is read-only, stays on your machine, and never sends your events anywhere else.",
	mockNote: 'You will land back in QuickNotch after granting access.',
	preparing: 'Preparing your sign-in…',
	ready: {
		eyebrow: 'Ready when you are',
		title: 'Sign in with Google to link your calendar',
		body: 'QuickNotch only reads your calendar. QuickNotch never uploads your events anywhere, and you can disconnect any time from Settings.'
	},
	signIn: 'Sign in with Google',
	backToApp: 'Back to QuickNotch',
	opening: 'Opening Google consent…',
	sent: 'Taking you to Google. If the tab did not open, try again.',
	openAgain: 'Open Google again',
	errorEyebrow: 'Something needs a fix',
	errorParse: 'Could not parse the sign-in request from the desktop app.',
	errorBody:
		'QuickNotch expects to hand this flow off to a small listener on your own machine. If the link expired, or you opened this page directly, start again from QuickNotch → Settings → Google Calendar.',
	backToWebsite: 'Back to the website',
	debugSummary: 'Technical details (for debugging)'
} as const;

/** The page Google bounces back to before the loopback hop. */
export const oauthPage = {
	title: 'Returning to QuickNotch',
	description:
		'Google sent you back; this page hands the sign-in to the QuickNotch app. Opened by the OAuth flow, not linked from the site.',
	returning: 'Returning to QuickNotch…',
	returningBody:
		"Google gave QuickNotch permission. We're sending you back to the app so it can finish signing you in. You can close this tab once QuickNotch says it's connected.",
	refusedEyebrow: "Sign-in didn't complete",
	refusedBody:
		'No permissions were changed. Taking you back to QuickNotch, which knows what this refusal means, since a cancelled consent and an unverified one look identical from here.',
	backNow: 'Back to QuickNotch now',
	errorEyebrow: 'Could not return to QuickNotch',
	errorBody:
		'Try connecting again from QuickNotch → Settings → Google Calendar. If this keeps happening, the state from the desktop app might have expired or the browser blocked the loopback jump.',
	startAgain: 'Start again',
	errors: {
		refused: (code: string, description: string) =>
			`Google returned "${code}"${description ? ` (${description})` : ''}.`,
		noCode: 'Google did not send an authorization code back.',
		noState: 'The callback is missing the state parameter.',
		noPort: 'The state does not tell QuickNotch which port to connect to.'
	}
} as const;

/** What the mock notch shows; it lives here so the demo and the app's screenshots never drift apart. */
export type GaugeIcon = 'cpu' | 'gpu' | 'ram' | 'storage' | 'download' | 'upload';
export type WeatherIcon = 'sun' | 'cloud' | 'partly' | 'rain';

type NotchDemo = {
	time: string;
	date: string;
	temp: string;
	notifications: string;
	gauges: { icon: GaugeIcon; name: string; value: string; percent: number }[];
	forecast: { dow: string; today?: boolean; icon: WeatherIcon; high: string; low: string }[];
	dates: { label: string; count: number }[];
	hud: { label: string; value: string }[];
	weather: { now: string; place: string; highLow: string; detail: string };
	calendar: { day: string; weekday: string; month: string };
	music: { title: string; artist: string };
};

export const notchDemo: NotchDemo = {
	time: '2:52',
	date: '25 Oct',
	temp: '23°',
	notifications: '3',
	gauges: [
		{ icon: 'cpu', name: 'CPU', value: '34%', percent: 34 },
		{ icon: 'gpu', name: 'GPU', value: '12%', percent: 12 },
		{ icon: 'ram', name: 'Memory', value: '61%', percent: 61 },
		{ icon: 'storage', name: 'Storage', value: '48%', percent: 48 },
		{ icon: 'download', name: 'Download', value: '1.2M/s', percent: 26 },
		{ icon: 'upload', name: 'Upload', value: '8K/s', percent: 6 }
	],
	forecast: [
		{ dow: 'Today', today: true, icon: 'sun', high: '23°', low: '16°' },
		{ dow: 'Tue', icon: 'partly', high: '21°', low: '15°' },
		{ dow: 'Wed', icon: 'cloud', high: '19°', low: '14°' },
		{ dow: 'Thu', icon: 'rain', high: '17°', low: '12°' },
		{ dow: 'Fri', icon: 'sun', high: '20°', low: '13°' },
		{ dow: 'Sat', icon: 'partly', high: '22°', low: '15°' },
		{ dow: 'Sun', icon: 'cloud', high: '18°', low: '13°' }
	],
	dates: [
		{ label: '24 Oct', count: 2 },
		{ label: '27 Oct', count: 1 },
		{ label: '29 Oct', count: 3 },
		{ label: '31 Oct', count: 1 }
	],
	hud: [
		{ label: 'FPS', value: '144' },
		{ label: 'CPU', value: '38%' },
		{ label: 'GPU', value: '91%' },
		{ label: 'PING', value: '12 ms' },
		{ label: 'NET', value: '1.4M/s' }
	],
	weather: {
		now: '23°C',
		place: 'Berlin',
		highLow: 'H 23° · L 16°',
		detail: '20% precip · UV 3'
	},
	calendar: { day: '23', weekday: 'Fri', month: 'Oct' },
	music: { title: 'Midnight City', artist: 'M83 · Hurry Up, We\u2019re Dreaming' }
};

/** The menu bar and clock drawn inside a mocked screen. */
export const desktopChrome = {
	menu: ['File', 'Edit', 'View', 'Window'],
	menuShort: ['File', 'Edit', 'View'],
	clock: '9:41',
	tourClock: '2:52 pm'
} as const;

/** The calendar page mock used by the connect screen. */
export const calendarDemo = {
	weekday: 'Thursday',
	connecting: 'Connecting',
	day: '26',
	days: ['Thu', 'Fri', 'Sat', 'Sun'],
	times: ['9:30', '13:00', '16:45', '19:15'],
	event: 'Stand-up with the team · in 12 min',
	waiting: 'Awaiting the code…',
	count: '2',
	idle: '·'
} as const;

/** Abstract dock tiles, original artwork rather than third-party app icons. */
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
 * Google Calendar auth: the token exchange stays in the desktop app because the site is a static host and Google requires a client_secret.
 * `callbackUrl` is the only registered redirect URI; the connect page takes the client id from the desktop.
 */
export const calendarAuth = {
	authEndpoint: 'https://accounts.google.com/o/oauth2/v2/auth',
	// No client id here: the desktop sends its own. The client is a Web application one (a Desktop client cannot register an https redirect URI); its secret is injected into the desktop build.
	// The connect page uses the redirect_uri the desktop sends, so this constant only documents the production URI to register.
	callbackUrl: 'https://quicknotch.app/oauth/callback',
	scope:
		'https://www.googleapis.com/auth/calendar.readonly https://www.googleapis.com/auth/userinfo.email'
} as const;
