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
const DISPLAY = "'Space Grotesk','Inter','Helvetica Neue',Helvetica,Arial,sans-serif";

const escape = (value) =>
	value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

const dots = `
	<g transform="translate(600,470)">
		<rect x="-164" y="-22" width="328" height="44" rx="22" fill="#0A0A0A" stroke="#0b1220" stroke-opacity="0.12"/>
		<circle cx="-104" cy="0" r="7" fill="#ffffff" fill-opacity="0.34"/>
		<rect x="-76" y="-7" width="34" height="14" rx="7" fill="#ffffff" fill-opacity="0.18"/>
		<circle cx="-24" cy="0" r="7" fill="#ffffff" fill-opacity="0.34"/>
		<g>
			<rect x="24" y="-17" width="34" height="34" rx="9" fill="#6366f1"/>
			<rect x="66" y="-17" width="34" height="34" rx="9" fill="#0891b2"/>
			<rect x="108" y="-17" width="34" height="34" rx="9" fill="#7e22ce"/>
		</g>
	</g>
`;

const ogSvg = `<svg xmlns="http://www.w3.org/2000/svg" width="1200" height="630" viewBox="0 0 1200 630">
	<defs>
		<pattern id="grid" width="60" height="60" patternUnits="userSpaceOnUse">
			<path d="M60 0H0V60" fill="none" stroke="#0b1220" stroke-opacity="0.035"/>
		</pattern>
	</defs>

	<rect width="1200" height="630" fill="#f7f9fc"/>
	<rect width="1200" height="630" fill="url(#grid)"/>

	<!-- Flat ambient tints: no gradients, just translucent flat discs. -->
	<circle cx="190" cy="20" r="320" fill="#4f46e5" fill-opacity="0.08"/>
	<circle cx="1100" cy="60" r="270" fill="#0891b2" fill-opacity="0.07"/>
	<circle cx="600" cy="660" r="330" fill="#7e22ce" fill-opacity="0.06"/>

	<g>
		<rect x="500" y="70" width="200" height="34" rx="17" fill="#ffffff" fill-opacity="0.7" stroke="#0b1220" stroke-opacity="0.1"/>
		<text x="600" y="92" text-anchor="middle" font-family="${FONT}" font-size="14" font-weight="600" fill="#475569" letter-spacing="2.4">WINDOWS &amp; macOS</text>
	</g>

	<text x="600" y="215" text-anchor="middle" font-family="${DISPLAY}" font-size="96" font-weight="700" fill="#0b1220" letter-spacing="-4">Your apps, one</text>
	<text x="600" y="315" text-anchor="middle" font-family="${DISPLAY}" font-size="96" font-weight="700" fill="#4338ca" letter-spacing="-4">flick away.</text>

	<text x="600" y="378" text-anchor="middle" font-family="${FONT}" font-size="24" font-weight="400" fill="#475569">A glanceable notch that shows your apps, weather, stats and live controls.</text>

	${dots}

	<rect x="60" y="556" width="1080" height="1" fill="#0b1220" fill-opacity="0.08"/>
	<text x="60" y="600" font-family="${FONT}" font-size="17" font-weight="600" fill="#0b1220" letter-spacing="0.2">QuickNotch</text>
	<text x="1140" y="600" text-anchor="end" font-family="${FONT}" font-size="17" font-weight="500" fill="#64748b">Free · Windows &amp; macOS</text>
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
		.flatten({ background: '#4f46e5' })
		.png({ compressionLevel: 9, effort: 10 })
		.toBuffer();
	await writePng('apple-touch-icon.png', touch, 180);

	console.log('Done.');
}

main().catch((error) => {
	console.error(error);
	process.exit(1);
});
