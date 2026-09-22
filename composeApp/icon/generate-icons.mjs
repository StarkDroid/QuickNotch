#!/usr/bin/env node
/**
 * Renders the QuickNotch brand mark (indigo rounded square + white notch glyph,
 * identical to the web header's `.brand__mark`) into every platform icon format:
 *
 *   composeApp/quicknotch.ico                       Windows taskbar/tray + installers
 *   composeApp/quicknotch.icns                      macOS Dock/Finder (via `iconutil`)
 *   composeApp/src/desktopMain/resources/quicknotch-tray.png   runtime tray icon
 *   composeApp/src/commonMain/composeResources/drawable/quicknotch_icon.png   settings UI
 *
 * Requires `sharp` (already present in web/node_modules) and, for the .icns step,
 * macOS `iconutil`. Regenerate with:
 *
 *   node composeApp/icon/generate-icons.mjs
 *
 * The generated binaries are committed, so this is not part of the Gradle build.
 */
import { mkdtemp, writeFile, copyFile, mkdir } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const root = join(dirname(fileURLToPath(import.meta.url)), '..', '..');
const sharp = require(join(root, 'web', 'node_modules', 'sharp'));

const iconDir = join(root, 'composeApp', 'icon');
const resourcesDir = join(root, 'composeApp', 'src', 'desktopMain', 'resources');
const svg = await readSvg();

const PNG_SIZES = [16, 24, 32, 48, 64, 128, 256, 512, 1024];
const ICO_SIZES = [16, 24, 32, 48, 64, 128, 256];

async function readSvg() {
	const { readFile } = await import('node:fs/promises');
	return readFile(join(iconDir, 'quicknotch-icon.svg'));
}

async function render(svg, size) {
	return sharp(svg).resize(size, size).png().toBuffer();
}

// All rasterized sizes, one buffer each.
const temp = await mkdtemp(join(tmpdir(), 'quicknotch-icons-'));
const pngs = new Map();
for (const size of PNG_SIZES) {
	const buffer = await render(svg, size);
	pngs.set(size, buffer);
	const file = join(temp, `${size}.png`);
	await writeFile(file, buffer);
	console.log(`  rendered ${size}x${size} -> ${file}`);
}

// --- Windows .ico (PNG-compressed entries, Vista+ compatible) -----------------
function buildIco(pngBuffers) {
	const count = pngBuffers.length;
	const entryBytes = 16;
	const dataStart = 6 + count * entryBytes;
	const header = Buffer.alloc(6);
	header.writeUInt16LE(0, 0);
	header.writeUInt16LE(1, 2);
	header.writeUInt16LE(count, 4);
	let offset = dataStart;
	const entries = [];
	for (const { size, buffer } of pngBuffers) {
		const entry = Buffer.alloc(entryBytes);
		entry.writeUInt8(size >= 256 ? 0 : size, 0);
		entry.writeUInt8(size >= 256 ? 0 : size, 1);
		entry.writeUInt8(0, 2);
		entry.writeUInt8(0, 3);
		entry.writeUInt16LE(1, 4);
		entry.writeUInt16LE(32, 6);
		entry.writeUInt32LE(buffer.length, 8);
		entry.writeUInt32LE(offset, 12);
		entries.push(entry);
		offset += buffer.length;
	}
	return Buffer.concat([header, ...entries, ...pngBuffers.map(({ buffer }) => buffer)]);
}

const ico = buildIco(ICO_SIZES.map((size) => ({ size, buffer: pngs.get(size) })));
await writeFile(join(iconDir, '..', 'quicknotch.ico'), ico);
console.log(`  wrote composeApp/quicknotch.ico (${ico.length} bytes)`);

// --- macOS .iconset (input for `iconutil -c icns`) ---------------------------
const iconset = join(temp, 'QuickNotch.iconset');
await mkdir(iconset);
const iconSet = {
	'icon_16x16.png': 16,
	'icon_16x16@2x.png': 32,
	'icon_32x32.png': 32,
	'icon_32x32@2x.png': 64,
	'icon_128x128.png': 128,
	'icon_128x128@2x.png': 256,
	'icon_256x256.png': 256,
	'icon_256x256@2x.png': 512,
	'icon_512x512.png': 512,
	'icon_512x512@2x.png': 1024
};
for (const [name, size] of Object.entries(iconSet)) {
	await copyFile(join(temp, `${size}.png`), join(iconset, name));
}
console.log(`  built ${iconset}`);

// --- Runtime tray icon PNG ----------------------------------------------------
// 32px matches AWT's natural menu-bar/notification sizing; Windows autoscales.
await mkdir(resourcesDir, { recursive: true });
await writeFile(join(resourcesDir, 'quicknotch-tray.png'), pngs.get(32));
console.log('  wrote composeApp/src/desktopMain/resources/quicknotch-tray.png');

// --- Settings UI header icon --------------------------------------------------
// 128px stays crisp at 2x for the settings header and about-dialog marks; it
// lives in composeResources so the UI loads it through generated Res accessors.
const drawableDir = join(root, 'composeApp', 'src', 'commonMain', 'composeResources', 'drawable');
await mkdir(drawableDir, { recursive: true });
await writeFile(join(drawableDir, 'quicknotch_icon.png'), pngs.get(128));
console.log('  wrote composeApp/src/commonMain/composeResources/drawable/quicknotch_icon.png');

console.log('\nNext:  iconutil -c icns <iconset> -o composeApp/quicknotch.icns');