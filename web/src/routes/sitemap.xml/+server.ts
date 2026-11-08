import { changelog } from '$data/releases';
import { site } from '$data/site';

/**
 * Prerendered at build time from the committed release feed, so `lastmod`
 * moves with every release instead of drifting in a static file.
 */
export const prerender = true;

function escape(value: string): string {
	return value
		.replace(/&/g, '&amp;')
		.replace(/</g, '&lt;')
		.replace(/>/g, '&gt;')
		.replace(/"/g, '&quot;');
}

export function GET() {
	const lastmod = (changelog.updatedAt ?? changelog.releases[0]?.date ?? '').slice(0, 10);
	const image = `${site.url}/og.png`;

	const pages = [
		{ loc: `${site.url}/`, changefreq: 'monthly', priority: '1.0', image: true },
		{ loc: `${site.url}/download`, changefreq: 'weekly', priority: '0.9', image: false },
		{ loc: `${site.url}/changelog`, changefreq: 'weekly', priority: '0.6', image: false }
	];

	const urls = pages
		.map((page) => {
			const lines = [
				'\t<url>',
				`\t\t<loc>${page.loc}</loc>`,
				lastmod ? `\t\t<lastmod>${lastmod}</lastmod>` : '',
				`\t\t<changefreq>${page.changefreq}</changefreq>`,
				`\t\t<priority>${page.priority}</priority>`
			];
			if (page.image) {
				lines.push(
					'\t\t<image:image>',
					`\t\t\t<image:loc>${image}</image:loc>`,
					`\t\t\t<image:title>${escape('QuickNotch, a floating notch app and app dock for Windows and macOS')}</image:title>`,
					'\t\t</image:image>'
				);
			}
			lines.push('\t</url>');
			return lines.filter(Boolean).join('\n');
		})
		.join('\n');

	const xml = `<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9" xmlns:image="http://www.google.com/schemas/sitemap-image/1.1">
${urls}
</urlset>
`;

	return new Response(xml, {
		headers: { 'Content-Type': 'application/xml; charset=utf-8' }
	});
}
