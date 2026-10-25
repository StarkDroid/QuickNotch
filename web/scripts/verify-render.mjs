/**
 * Runtime smoke test for the built site.
 *
 * Uses the Chrome DevTools Protocol (no extra dependencies — Node ships `fetch`
 * and `WebSocket`) to check the things a static build cannot prove:
 *   - the page actually hydrates and the scroll-reveal actions run
 *   - the hero canvas mounts with real dimensions
 *   - nothing overflows horizontally at desktop or mobile widths
 *   - no uncaught page errors or console errors
 *
 * Usage:  node scripts/verify-render.mjs [url]
 * Requires Google Chrome. Start a server first, e.g.
 *   python3 -m http.server 4173 --directory build
 */

import { spawn } from 'node:child_process';
import { rmSync } from 'node:fs';
import { randomUUID } from 'node:crypto';
import { setTimeout as sleep } from 'node:timers/promises';
import sharp from 'sharp';

const CHROME =
	process.env.CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const url = process.argv[2] || 'http://localhost:4173/';
// Random port: a fixed one collides with a browser leaked by an earlier run.
const port = 9400 + Math.floor(Math.random() * 500);

// A throwaway profile per run: a reused one lets Chrome restore the previous
// session's tab, and the script would then inspect a stale page.
const profile = `${process.env.TMPDIR || '/tmp'}/qn-cdp-${randomUUID()}`;

const chrome = spawn(
	CHROME,
	[
		'--headless=new',
		`--remote-debugging-port=${port}`,
		`--user-data-dir=${profile}`,
		'--no-first-run',
		'--no-default-browser-check',
		'--disable-gpu',
		'--hide-scrollbars',
		'--window-size=1440,1000',
		url
	],
	{ stdio: 'ignore' }
);

async function findTarget() {
	for (let attempt = 0; attempt < 80; attempt++) {
		try {
			const list = await (await fetch(`http://127.0.0.1:${port}/json/list`)).json();
			const pages = list.filter((t) => t.type === 'page' && t.webSocketDebuggerUrl);
			// Prefer the tab we actually asked for.
			const match = pages.find((t) => t.url.startsWith(url));
			if (match) return match;
			if (pages.length === 1) return pages[0];
		} catch {
			/* devtools not up yet */
		}
		await sleep(200);
	}
	throw new Error('Could not find a Chrome DevTools page target');
}

const cleanup = () => {
	try {
		chrome.kill();
	} catch {
		/* already gone */
	}
	try {
		rmSync(profile, { recursive: true, force: true });
	} catch {
		/* best effort */
	}
};

// Never leave a headless browser behind, even if a probe throws.
process.on('exit', cleanup);
process.on('uncaughtException', (error) => {
	console.error(error);
	cleanup();
	process.exit(1);
});

const page = await findTarget();
const ws = new WebSocket(page.webSocketDebuggerUrl);
await new Promise((resolve, reject) => {
	ws.onopen = resolve;
	ws.onerror = reject;
});

let nextId = 0;
const pending = new Map();
const consoleErrors = [];

ws.onmessage = (event) => {
	const message = JSON.parse(event.data);
	if (message.method === 'Runtime.exceptionThrown') {
		consoleErrors.push(
			`uncaught: ${message.params.exceptionDetails?.exception?.description ?? 'unknown'}`
		);
	}
	if (message.method === 'Runtime.consoleAPICalled' && message.params.type === 'error') {
		consoleErrors.push(message.params.args.map((a) => a.value ?? a.description).join(' '));
	}
	if (message.id && pending.has(message.id)) {
		pending.get(message.id)(message);
		pending.delete(message.id);
	}
};

const send = (method, params = {}) =>
	new Promise((resolve) => {
		const id = ++nextId;
		pending.set(id, resolve);
		ws.send(JSON.stringify({ id, method, params }));
	});

const evaluate = async (expression) => {
	const response = await send('Runtime.evaluate', {
		expression,
		returnByValue: true,
		awaitPromise: true
	});
	const details = response.result?.exceptionDetails;
	if (details) {
		const described =
			details.exception?.description ?? details.text ?? 'unknown evaluation error';
		throw new Error(`${described} — while evaluating: ${expression.slice(0, 120)}`);
	}
	return response.result?.result?.value;
};

await send('Runtime.enable');
await send('Page.enable');
await sleep(4000); // hydrate, then idle-mount the deferred work

const line = (label, value) => console.log(`${label.padEnd(26)} ${value}`);

// Report page health first: a hydration crash blanks the page, and every probe
// below would then fail in confusing ways.
console.log('\n── Page health ────────────────────────────────────');
const pageNoise = /favicon/i;
const pageErrors = consoleErrors.filter((message) => !pageNoise.test(message));
if (pageErrors.length === 0) {
	line('uncaught / console errors', 'none');
} else {
	line('uncaught / console errors', `${pageErrors.length} FOUND`);
	pageErrors.slice(0, 6).forEach((message) => console.log(`  ${message.slice(0, 260)}`));
}

/** Probes are isolated so one broken probe cannot abort the whole report. */
const safe = async (label, expression) => {
	try {
		return await evaluate(expression);
	} catch (error) {
		console.log(`  ! probe "${label}" failed: ${String(error.message).slice(0, 180)}`);
		return null;
	}
};

/**
 * Measures the luminance standard deviation of an empty background region from
 * a screenshot. The grain and spotlight live on negative z-index fixed layers;
 * a regression where an opaque ancestor buries them makes this flat (std ≈ 0).
 */
async function backgroundStd() {
	await evaluate(`window.scrollTo(0, 0)`);
	await sleep(1200);
	const shot = await send('Page.captureScreenshot', { format: 'png', fromSurface: true });
	const { data, info } = await sharp(Buffer.from(shot.result.data, 'base64'))
		.removeAlpha()
		.raw()
		.toBuffer({ resolveWithObject: true });
	const { width, channels } = info;

	// Top-left corner: below the nav, clear of the headline and the top glow.
	const box = { x: 24, y: 96, w: 180, h: 110 };
	let sum = 0;
	let n = 0;
	for (let y = box.y; y < box.y + box.h; y++) {
		for (let x = box.x; x < box.x + box.w; x++) {
			const idx = (y * width + x) * channels;
			sum += 0.2126 * data[idx] + 0.7152 * data[idx + 1] + 0.0722 * data[idx + 2];
			n++;
		}
	}
	const mean = sum / n;
	let variance = 0;
	for (let y = box.y; y < box.y + box.h; y++) {
		for (let x = box.x; x < box.x + box.w; x++) {
			const idx = (y * width + x) * channels;
			const l = 0.2126 * data[idx] + 0.7152 * data[idx + 1] + 0.0722 * data[idx + 2];
			variance += (l - mean) ** 2;
		}
	}
	return Math.sqrt(variance / n);
}

const revealReport = `(() => {
	const all = [...document.querySelectorAll('[data-reveal]')];
	const staggers = [...document.querySelectorAll('[data-stagger]')];
	const shown = all.filter((el) => el.classList.contains('is-in'));
	return {
		revealTotal: all.length,
		revealShown: shown.length,
		staggerTotal: staggers.length,
		staggerShown: staggers.filter((el) => el.classList.contains('is-in')).length,
		hiddenOpacity: all.filter((el) => getComputedStyle(el).opacity === '0').length,
		scrollHeight: document.documentElement.scrollHeight
	};
})()`;

const spotlightReport = `(() => {
	const core = document.querySelector('.spotlight__core');
	if (!core) return null;
	const r = core.getBoundingClientRect();
	return {
		x: Math.round(r.left + r.width / 2),
		y: Math.round(r.top + r.height / 2),
		width: Math.round(r.width),
		opacity: getComputedStyle(core).opacity
	};
})()`;

/** Scrolls an element's centre to the viewport centre, then lets it settle. */
const heroReport = `(() => {
	const h1 = document.querySelector('.hero__title');
	const masks = [...h1.querySelectorAll('.line-mask')];
	const spans = masks.map((m) => m.querySelector('[data-line]'));
	const extras = [...document.querySelectorAll('.hero [data-hero-extra]')];
	const label = (el) => el.className.toString().split(' ')[0] || el.tagName.toLowerCase();
	const dim = (els) => els.filter((el) => parseFloat(getComputedStyle(el).opacity) < 0.99);

	// The headline lines sit inside overflow-hidden masks, so a line left offset
	// from its resting position is silently clipped — measure the offset itself
	// rather than the h1's box, which stays put either way.
	const offsets = spans.map((span, i) =>
		Math.round(span.getBoundingClientRect().top - masks[i].getBoundingClientRect().top)
	);

	// Real font bounds, so the reported top gap is the distance to the ink, not
	// to the layout box.
	const cs = getComputedStyle(h1);
	const ctx = document.createElement('canvas').getContext('2d');
	ctx.font = cs.fontWeight + ' ' + cs.fontSize + ' ' + cs.fontFamily;
	const metrics = ctx.measureText('YOUR APPS');
	const lineHeight = parseFloat(cs.lineHeight);
	const halfLeading = (lineHeight - (metrics.fontBoundingBoxAscent + metrics.fontBoundingBoxDescent)) / 2;
	const first = spans[0].getClientRects()[0];
	const inkTop = first.top + halfLeading + metrics.fontBoundingBoxAscent - metrics.actualBoundingBoxAscent;
	const nav = document.querySelector('.nav').getBoundingClientRect();

	return {
		lineCount: spans.length,
		linesHidden: dim(spans).map(label),
		lineOffsets: offsets,
		extraCount: extras.length,
		extrasHidden: dim(extras).map(label),
		staticFallback: document.querySelector('.hero--static') !== null,
		navBottom: Math.round(nav.bottom),
		h1BoxTop: Math.round(h1.getBoundingClientRect().top),
		inkTop: Math.round(inkTop)
	};
})()`;

const designReport = `(() => {
	// Painted gradients only. A mask-image fade is a crop, not a painted gradient,
	// so maskImage is reported separately rather than lumped in here.
	const painted = [];
	const masked = [];
	for (const el of document.querySelectorAll('body *')) {
		const cs = getComputedStyle(el);
		if (cs.backgroundImage.includes('gradient(')) {
			painted.push(el.tagName.toLowerCase() + '.' + (el.className.toString().split(' ')[0] || ''));
		}
		if (cs.maskImage?.includes('gradient(') || cs.webkitMaskImage?.includes('gradient(')) {
			masked.push(el.tagName.toLowerCase() + '.' + (el.className.toString().split(' ')[0] || ''));
		}
	}

	const grain = document.querySelector('.grain');
	const grainStyle = grain ? getComputedStyle(grain) : null;

	return {
		paintedGradients: painted.slice(0, 8),
		paintedGradientCount: painted.length,
		maskFades: [...new Set(masked)].slice(0, 8),
		grain: grain
			? {
					hasTexture: grainStyle.backgroundImage.startsWith('url("data:image/png'),
					opacity: grainStyle.opacity,
					zIndex: grainStyle.zIndex,
					pointerEvents: grainStyle.pointerEvents
				}
			: null
	};
})()`;

const firstPaint = (await safe('reveal report', revealReport)) ?? {
	revealTotal: 0,
	revealShown: 0,
	staggerTotal: 0,
	staggerShown: 0,
	hiddenOpacity: 0,
	scrollHeight: 0
};
const design = (await safe('design report', designReport)) ?? {
	paintedGradients: [],
	paintedGradientCount: -1,
	maskFades: [],
	grain: null
};

// Scroll through the page in steps so every IntersectionObserver fires. A
// single jump to the bottom can skip callbacks entirely.
const pageHeight = await evaluate(`document.documentElement.scrollHeight`);
for (const fraction of [0.15, 0.3, 0.45, 0.6, 0.75, 0.9, 1]) {
	await evaluate(`window.scrollTo(0, ${Math.round(pageHeight * fraction)})`);
	await sleep(500);
}
await sleep(900);
await evaluate(`window.scrollTo(0, 0)`);
await sleep(1600);

const afterScroll = (await safe('reveal report (scrolled)', revealReport)) ?? firstPaint;
const hero = (await safe('hero report', heroReport)) ?? {
	lineCount: 0,
	linesHidden: [],
	lineOffsets: [],
	extraCount: 0,
	extrasHidden: [],
	staticFallback: false,
	navBottom: 0,
	h1BoxTop: 0,
	inkTop: 0
};

/**
 * The scroll tour is the one interactive centrepiece: probe it at three depths
 * of its pin and confirm the hinge actually opens and the step advances.
 */
const tourReport = async () => {
	const pin = await safe(
		'tour pin',
		`(() => {
			const el = document.querySelector('.tour__pin');
			if (!el) return null;
			const r = el.getBoundingClientRect();
			return { top: Math.round(r.top + window.scrollY), height: Math.round(r.height) };
		})()`
	);
	if (!pin) return null;

	const read = async () => {
		return await safe(
			'tour state',
			`(() => {
				const tour = document.querySelector('#tour');
				const device = document.querySelector('.tour__device');
				const stepEl = document.querySelector('.tour__step.active');
				return {
					step: Number(tour?.dataset.step ?? -1),
					lid: (device?.style.getPropertyValue('--lid') ?? '').trim(),
					caption: stepEl?.querySelector('.tour__title')?.textContent ?? '',
					visible: !!device && device.getBoundingClientRect().width > 0
				};
			})()`
		);
	};

	const samples = [];
	for (const fraction of [0.02, 0.42, 0.92]) {
		const y = pin.top + Math.round(pin.height * fraction);
		await evaluate(`window.scrollTo(0, ${y})`);
		await sleep(900);
		samples.push(await read());
	}
	await evaluate(`window.scrollTo(0, 0)`);
	await sleep(800);
	return { pin, samples };
};

const tour = await tourReport();

// Walk the spotlight down the page and confirm it tracks and lands on target.
await evaluate(`window.scrollTo(0, 0)`);
await sleep(1400);
const spotTop = await safe('spotlight at top', spotlightReport);

await safe('scroll to #look', `(() => {
	const el = document.querySelector('#look .look__row');
	if (!el) return false;
	const r = el.getBoundingClientRect();
	window.scrollTo(0, r.top + window.scrollY - window.innerHeight / 2);
	return true;
})()`);
await sleep(1700);
const spotMid = await safe('spotlight at #look', spotlightReport);

await safe('scroll to #download', `(() => {
	const el = document.querySelector('#download .actions');
	if (!el) return false;
	const r = el.getBoundingClientRect();
	window.scrollTo(0, r.top + window.scrollY - window.innerHeight / 2);
	return true;
})()`);
await sleep(2100);
const spotEnd = await safe('spotlight at #download', spotlightReport);
const downloadTarget = await safe(
	'download target rect',
	`(() => {
	const el = document.querySelector('#download .actions');
	if (!el) return null;
	const r = el.getBoundingClientRect();
	return { x: Math.round(r.left + r.width / 2), y: Math.round(r.top + r.height / 2) };
})()`
);
await evaluate(`window.scrollTo(0, 0)`);

const overflowProbe = (width) => `(() => {
	const vw = ${width};
	const offenders = [...document.querySelectorAll('body *')]
		.map((el) => ({ el, rect: el.getBoundingClientRect() }))
		.filter(({ rect }) => rect.width > 0 && (rect.right > vw + 1.5 || rect.left < -1.5))
		.slice(0, 6)
		.map(({ el, rect }) => ({
			tag: el.tagName.toLowerCase(),
			cls: (el.className || '').toString().slice(0, 60),
			left: Math.round(rect.left),
			right: Math.round(rect.right)
		}));
	return {
		docScrollWidth: document.documentElement.scrollWidth,
		bodyScrollWidth: document.body.scrollWidth,
		innerWidth: window.innerWidth,
		offenders
	};
})()`;

await send('Emulation.setDeviceMetricsOverride', {
	width: 1440,
	height: 900,
	deviceScaleFactor: 1,
	mobile: false
});
await sleep(600);
const desktopOverflow = await safe('desktop overflow', overflowProbe(1440));

await send('Emulation.setDeviceMetricsOverride', {
	width: 390,
	height: 844,
	deviceScaleFactor: 2,
	mobile: true
});
await sleep(900);
const mobileOverflow = await safe('mobile overflow', overflowProbe(390));

console.log('\n── Reveal / hydration ──────────────────────────────');
line('data-reveal total', firstPaint.revealTotal);
line(
	'revealed on load',
	`${firstPaint.revealShown}  (stagger ${firstPaint.staggerShown}/${firstPaint.staggerTotal})`
);
line(
	'revealed after scroll',
	`${afterScroll.revealShown}/${afterScroll.revealTotal}  (stagger ${afterScroll.staggerShown}/${afterScroll.staggerTotal})`
);
line('still at opacity 0', afterScroll.hiddenOpacity);
line('document height', firstPaint.scrollHeight);

console.log('\n── Spotlight ──────────────────────────────────────');
if (!spotTop || !spotMid || !spotEnd) {
	line('spotlight', 'NOT RENDERED');
} else {
	line('at top', `${spotTop.x}, ${spotTop.y} · core ${spotTop.width}px`);
	line('at #look', `${spotMid.x}, ${spotMid.y} · core ${spotMid.width}px`);
	line('at #download', `${spotEnd.x}, ${spotEnd.y} · core ${spotEnd.width}px`);
	line(
		'distance to CTA',
		downloadTarget
			? `${Math.round(Math.hypot(spotEnd.x - downloadTarget.x, spotEnd.y - downloadTarget.y))}px`
			: 'no target'
	);
	line(
		'tracks scroll',
		spotTop.y !== spotMid.y || spotMid.y !== spotEnd.y ? 'yes' : 'NO — it never moved'
	);
}

console.log('\n── Scroll tour ─────────────────────────────────────');
if (!tour) {
	line('tour', 'NOT RENDERED');
} else {
	tour.samples.forEach((sample, index) => {
		line(
			`at ${index === 0 ? 'start' : index === 1 ? 'middle' : 'end'}`,
			`step ${sample.step} · lid ${sample.lid || 'none'} · ${sample.caption}`
		);
	});
	const advanced = tour.samples[2].step > tour.samples[0].step;
	const opened = parseFloat(tour.samples[2].lid) > parseFloat(tour.samples[0].lid);
	line('step advances', advanced ? 'yes' : 'NO');
	line('lid opens', opened ? 'yes' : 'NO');
}

console.log('\n── Hero entrance ───────────────────────────────────');
line('headline lines', `${hero.lineCount} (hidden: ${hero.linesHidden.join(', ') || 'none'})`);
line('line offset in mask', `${hero.lineOffsets.join(', ')}  (must be 0)`);
line('hero extras', `${hero.extraCount} (hidden: ${hero.extrasHidden.join(', ') || 'none'})`);
line('static fallback used', hero.staticFallback ? 'YES — animation did not run' : 'no');
line('nav → h1 box', `${hero.h1BoxTop - hero.navBottom}px`);
line('nav → headline ink', `${hero.inkTop - hero.navBottom}px`);

console.log('\n── Design: gradients & grain ───────────────────────');
line(
	'painted gradients',
	design.paintedGradientCount === 0 ? '0 (clean)' : `${design.paintedGradientCount} FOUND`
);
design.paintedGradients.forEach((el) => line('  gradient on', el));
line('mask-only fades', design.maskFades.length === 0 ? 'none' : design.maskFades.join(', '));
line(
	'grain layer',
	design.grain
		? `texture ${design.grain.hasTexture ? 'generated' : 'MISSING'} · opacity ${design.grain.opacity} · z ${design.grain.zIndex}`
		: 'NOT RENDERED'
);

// Pixel-level confirmation: computed styles can be right while the layer is
// buried behind an opaque ancestor (it happened). Measure the real background.
const noiseStd = await backgroundStd();
line(
	'noise visible (pixel σ)',
	noiseStd === null
		? 'could not capture'
		: noiseStd >= 2.5
			? `${noiseStd.toFixed(1)} — present`
			: `${noiseStd.toFixed(2)} — FLAT, layer is buried`
);

console.log('\n── Horizontal overflow ─────────────────────────────');
for (const [name, report] of [
	['desktop 1440', desktopOverflow],
	['mobile 390', mobileOverflow]
]) {
	if (!report) {
		line(name, 'probe failed');
		continue;
	}
	line(name, `scrollWidth ${report.docScrollWidth} / inner ${report.innerWidth}`);
	for (const o of report.offenders) {
		line('  offender', `<${o.tag} class="${o.cls}"> ${o.left}→${o.right}`);
	}
}

console.log('\n── Console (after interaction) ─────────────────────');
const real = consoleErrors.filter((message) => !/favicon/i.test(message));
line('page errors', real.length === 0 ? 'none' : real.length);
real.slice(0, 8).forEach((message) => console.log(`  ${message.slice(0, 240)}`));

ws.close();
cleanup();
process.exit(0);
