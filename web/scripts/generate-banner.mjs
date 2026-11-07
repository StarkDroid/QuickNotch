/**
 * Rasterises `docs/banner.svg` into the PNG the README embeds.
 *
 * The SVG stays the source of truth so the banner is editable as text; GitHub
 * renders the PNG identically everywhere, which SVG text cannot promise (an
 * SVG loaded as an image only resolves fonts the viewer happens to own).
 *
 * Run with `npm run banner` from `web/`. The output is committed.
 */

import { readFile, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import sharp from 'sharp';

const root = join(dirname(fileURLToPath(import.meta.url)), '..', '..');
const source = join(root, 'docs', 'banner.svg');
const target = join(root, 'docs', 'banner.png');

const svg = await readFile(source);
const png = await sharp(svg, { density: 192 })
	.resize({ width: 2560 })
	.png({ compressionLevel: 9 })
	.toBuffer();

await writeFile(target, png);
console.log(`docs/banner.png  ${Math.round(png.length / 1024)} KB (2560px)`);
