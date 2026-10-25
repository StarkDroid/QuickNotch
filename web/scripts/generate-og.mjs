/**
 * Generates the raster assets that cannot live as SVG:
 *
 *   static/og.png               1200×630 social card
 *   static/icon-192.png         manifest icon
 *   static/icon-512.png         manifest icon
 *   static/apple-touch-icon.png iOS home-screen icon
 *
 * Run with `npm run og`. This is intentionally NOT part of `npm run build` so a
 * production build never depends on a native image pipeline; the generated files
 * are committed.
 *
 * The card matches the site's light theme and its gradient-free rule: every
 * accent is a flat colour, and depth comes from translucent flat shapes.
 */

import { readFile, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import sharp from 'sharp';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const staticDir = join(root, 'static');

const FONT = "'Inter','Helvetica Neue',Helvetica,Arial,sans-serif";
const DISPLAY = "'Fraunces','Georgia','Times New Roman',serif";

// Keep the card's price line in step with `pricing.label` in src/lib/data/site.ts
// (this script is plain Node and cannot import the TypeScript content module).
const PRICE_LABEL = '$9';

const escape = (value) =>
	value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

const dots = `
	<g transform="translate(600,470)">
		<rect x="-164" y="-22" width="328" height="44" rx="22" fill="#0A0A0A" stroke="#211c13" stroke-opacity="0.16"/>
		<circle cx="-104" cy="0" r="7" fill="#ffffff" fill-opacity="0.34"/>
		<rect x="-76" y="-7" width="34" height="14" rx="7" fill="#ffffff" fill-opacity="0.18"/>
		<circle cx="-24" cy="0" r="7" fill="#ffffff" fill-opacity="0.34"/>
		<g>
			<rect x="24" y="-17" width="34" height="34" rx="9" fill="#e8481c"/>
			<rect x="66" y="-17" width="34" height="34" rx="9" fill="#1f4fd8"/>
			<rect x="108" y="-17" width="34" height="34" rx="9" fill="#0f8fa6"/>
		</g>
	</g>
`;

const ogSvg = `<svg xmlns="http://www.w3.org/2000/svg" width="1200" height="630" viewBox="0 0 1200 630">
	<defs>
		<pattern id="grid" width="60" height="60" patternUnits="userSpaceOnUse">
			<path d="M60 0H0V60" fill="none" stroke="#211c13" stroke-opacity="0.045"/>
		</pattern>
	</defs>

	<rect width="1200" height="630" fill="#f9f3e7"/>
	<rect width="1200" height="630" fill="url(#grid)"/>

	<!-- Flat ambient tints: no gradients, just translucent flat discs. -->
	<circle cx="170" cy="40" r="330" fill="#e8481c" fill-opacity="0.09"/>
	<circle cx="1080" cy="80" r="280" fill="#1f4fd8" fill-opacity="0.08"/>
	<circle cx="600" cy="680" r="340" fill="#b83a10" fill-opacity="0.05"/>

	<rect x="500" y="90" width="200" height="34" rx="8" fill="#fffdf6" stroke="#211c13" stroke-width="2"/>
	<text x="600" y="112" text-anchor="middle" font-family="${FONT}" font-size="14" font-weight="700" fill="#b83a10" letter-spacing="2.4">WINDOWS &amp; MACOS</text>

	<text x="600" y="235" text-anchor="middle" font-family="${DISPLAY}" font-size="96" font-weight="700" fill="#211c13" letter-spacing="-4">Everything you need.</text>
	<text x="600" y="335" text-anchor="middle" font-family="${DISPLAY}" font-size="96" font-weight="700" fill="#b83a10" letter-spacing="-4">One tiny notch.</text>

	<text x="600" y="395" text-anchor="middle" font-family="${FONT}" font-size="24" font-weight="400" fill="#4c4437">The floating notch and app dock for Windows and macOS.</text>

	${dots}

	<rect x="60" y="556" width="1080" height="3" fill="#211c13" fill-opacity="0.12"/>
	<text x="60" y="600" font-family="${FONT}" font-size="17" font-weight="700" fill="#211c13" letter-spacing="0.2">QuickNotch — app dock, notch, widgets</text>
	<text x="1140" y="600" text-anchor="end" font-family="${FONT}" font-size="17" font-weight="500" fill="#4c4437">${escape(PRICE_LABEL)} · Windows &amp; macOS</text>
</svg>`;

async function writePng(name, buffer, width) {
	await writeFile(join(staticDir, name), buffer);
	console.log(`  ${name.padEnd(26)} ${Math.round(buffer.length / 1024)} KB${width ? ` (${width}px)` : ''}`);
}

async function main() {
	console.log('Generating assets…');

	const og = await sharp(Buffer.from(ogSvg), { density: 72 })
		.png({ compressionLevel: 9, effort: 10 })
		.toBuffer();
	await writePng('og.png', og, 1200);

	const favicon = await readFile(join(staticDir, 'favicon.svg'));

	for (const size of [192, 512]) {
		const icon = await sharp(favicon, { density: 1152 })
			.resize(size, size, { fit: 'contain', background: { r: 0, g: 0, b: 0, alpha: 0 } })
			.png({ compressionLevel: 9, effort: 10 })
			.toBuffer();
		await writePng(`icon-${size}.png`, icon, size);
	}

	const touch = await sharp(favicon, { density: 1152 })
		.resize(180, 180, { fit: 'contain', background: { r: 0, g: 0, b: 0, alpha: 0 } })
		.flatten({ background: '#e8481c' })
		.png({ compressionLevel: 9, effort: 10 })
		.toBuffer();
	await writePng('apple-touch-icon.png', touch, 180);

	console.log('Done.');
}

main().catch((error) => {
	console.error(error);
	process.exit(1);
});
