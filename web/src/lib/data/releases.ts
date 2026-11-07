/**
 * The published changelog is committed by the release workflow and
 * imported at build time, so the version and history are prerendered
 * without a GitHub call.
 */

import feed from './changelog.json';

export type ChangelogChange = { kind: string; text: string };

export type ChangelogRelease = {
	version: string;
	date: string;
	title: string;
	highlights: string[];
	changes: ChangelogChange[];
};

export type ChangelogFeed = {
	schema?: number;
	latest?: string;
	updatedAt?: string;
	releases: ChangelogRelease[];
};

/** The committed feed, newest release first. */
export const changelog: ChangelogFeed = feed as ChangelogFeed;

/** The version every download link points at. */
export const latest: string = changelog.latest || changelog.releases[0]?.version || '';

/** GitHub's asset URL for one published file. */
export function releaseAsset(version: string, file: string): string {
	return `https://github.com/StarkDroid/QuickNotch/releases/download/v${version}/${file}`;
}

/** The release page (notes, checksums, older builds). */
export function releasePage(version: string): string {
	return `https://github.com/StarkDroid/QuickNotch/releases/tag/v${version}`;
}

export type InstallerId = 'windows-msi' | 'windows-exe' | 'mac-arm64' | 'mac-intel';

export type Installer = {
	id: InstallerId;
	platform: 'windows' | 'macos';
	label: string;
	detail: string;
	file: (version: string) => string;
};

/**
 * Installer names follow the version in the feed. Windows ships .msi and
 * .exe; macOS ships arm64 and x64 DMGs.
 */
export const installers: Installer[] = [
	{
		id: 'windows-msi',
		platform: 'windows',
		label: 'MSI installer',
		detail: '.msi · x64',
		file: (version) => `QuickNotch-${version}.msi`
	},
	{
		id: 'windows-exe',
		platform: 'windows',
		label: 'EXE installer',
		detail: '.exe · x64',
		file: (version) => `QuickNotch-${version}.exe`
	},
	{
		id: 'mac-arm64',
		platform: 'macos',
		label: 'Apple silicon',
		detail: '.dmg · M-series',
		file: (version) => `QuickNotch-${version}-arm64.dmg`
	},
	{
		id: 'mac-intel',
		platform: 'macos',
		label: 'Intel',
		detail: '.dmg · x64',
		file: (version) => `QuickNotch-${version}-x64.dmg`
	}
];

export function installersFor(platform: 'windows' | 'macos'): Installer[] {
	return installers.filter((installer) => installer.platform === platform);
}

/** Which card to mark as the visitor's machine, or null when it is neither. */
export function platformOf(userAgent: string): 'windows' | 'macos' | null {
	if (/Windows/i.test(userAgent)) return 'windows';
	if (/Macintosh|Mac OS X/i.test(userAgent)) return 'macos';
	return null;
}
