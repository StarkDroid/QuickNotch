<script lang="ts">
	/**
	 * Single inline SVG icon set. Keeping every glyph local means zero icon-font
	 * requests and lets icons inherit `currentColor` from their context.
	 */

	type IconName =
		| 'logo'
		| 'windows'
		| 'apple'
		| 'download'
		| 'cart'
		| 'arrow-left'
		| 'arrow-right'
		| 'arrow-up-right'
		| 'check'
		| 'plus'
		| 'search'
		| 'settings'
		| 'tune'
		| 'apps'
		| 'toggle'
		| 'drag'
		| 'close'
		| 'chevron-down'
		| 'cursor'
		| 'sparkle'
		| 'bolt'
		| 'shield'
		| 'tray'
		| 'layers'
		| 'move'
		| 'terminal-ui'
		| 'palette-ui'
		| 'cpu'
		| 'gpu'
		| 'ram'
		| 'storage'
		| 'upload'
		| 'calendar'
		| 'box'
		| 'menu'
		| 'sun'
		| 'play'
		| 'pause'
		| 'next';

	interface Props {
		name: IconName;
		size?: number | string;
		stroke?: number;
		class?: string;
	}

	let { name, size = 20, stroke = 1.7, class: className = '' }: Props = $props();

	// Stroke-based 24×24 grid.
	const strokeIcons: Record<string, string> = {
		logo: '<rect x="2.5" y="8" width="19" height="8" rx="4"/><circle cx="7.5" cy="12" r="1.15" fill="currentColor" stroke="none"/><rect x="10.4" y="10.9" width="4.6" height="2.2" rx="1.1" fill="currentColor" stroke="none"/><circle cx="17.6" cy="12" r="1.15" fill="currentColor" stroke="none"/>',
		windows: '<path d="M3 5.6 10.4 4.5v7H3zM11.6 4.3 21 3v8.5h-9.4zM3 12.5h7.4v7L3 18.4zM11.6 12.5H21V21l-9.4-1.3z"/>',
		apple: '<path d="M16.2 12.7c0-2.3 1.9-3.4 2-3.5-1.1-1.6-2.7-1.8-3.3-1.8-1.4-.1-2.6.8-3.3.8-.7 0-1.7-.8-2.8-.8-1.5 0-2.8.9-3.6 2.2-1.5 2.6-.4 6.5 1.1 8.6.7 1 1.6 2.2 2.7 2.1 1.1 0 1.5-.7 2.8-.7s1.6.7 2.7.7c1.1 0 1.9-1 2.6-2 .8-1.2 1.1-2.3 1.2-2.4-.1 0-2.2-.9-2.2-3.2zM14.4 5.5c.6-.7 1-1.7.9-2.7-.9 0-2 .6-2.6 1.4-.6.6-1 1.6-.9 2.6 1 .1 2-.5 2.6-1.3z"/>',
		download: '<path d="M12 3v12m0 0 4.5-4.5M12 15l-4.5-4.5M4 17v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2"/>',
		cart: '<path d="M2.5 3.5h2.3l2.5 10.3a2 2 0 0 0 1.9 1.5h7.6a2 2 0 0 0 2-1.5l1.5-6.6H6"/><circle cx="9.7" cy="19.4" r="1.5"/><circle cx="17.1" cy="19.4" r="1.5"/>',
		'arrow-right': '<path d="M5 12h14m0 0-6-6m6 6-6 6"/>',
		'arrow-left': '<path d="M19 12H5m0 0 6-6m-6 6 6 6"/>',
		'arrow-up-right': '<path d="M7 17 17 7m0 0H8m9 0v9"/>',
		check: '<path d="m4.5 12.5 5 5 10-11"/>',
		plus: '<path d="M12 5v14M5 12h14"/>',
		search: '<circle cx="11" cy="11" r="7"/><path d="m20 20-3.6-3.6"/>',
		settings:
			'<circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-2.9 1.2v.2a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-2.9-1.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1A1.7 1.7 0 0 0 3 15a2 2 0 0 1 0-4h.2a1.7 1.7 0 0 0 1.2-2.9l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1A1.7 1.7 0 0 0 10 4.1V4a2 2 0 1 1 4 0v.2a1.7 1.7 0 0 0 2.9 1.2l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0 1.2 2.9H21a2 2 0 0 1 0 4h-.2a1.7 1.7 0 0 0-1.4 1z"/>',
		tune: '<path d="M4 6h10M18 6h2M4 12h4M12 12h8M4 18h12M20 18h0"/><circle cx="16" cy="6" r="2"/><circle cx="10" cy="12" r="2"/><circle cx="18" cy="18" r="2"/>',
		apps: '<rect x="3" y="3" width="7.5" height="7.5" rx="2"/><rect x="13.5" y="3" width="7.5" height="7.5" rx="2"/><rect x="3" y="13.5" width="7.5" height="7.5" rx="2"/><rect x="13.5" y="13.5" width="7.5" height="7.5" rx="2"/>',
		toggle:
			'<rect x="2" y="7.5" width="20" height="9" rx="4.5"/><circle cx="16.5" cy="12" r="2.6" fill="currentColor" stroke="none"/>',
		drag: '<circle cx="9" cy="6" r="1.3" fill="currentColor" stroke="none"/><circle cx="15" cy="6" r="1.3" fill="currentColor" stroke="none"/><circle cx="9" cy="12" r="1.3" fill="currentColor" stroke="none"/><circle cx="15" cy="12" r="1.3" fill="currentColor" stroke="none"/><circle cx="9" cy="18" r="1.3" fill="currentColor" stroke="none"/><circle cx="15" cy="18" r="1.3" fill="currentColor" stroke="none"/>',
		close: '<path d="M6 6 18 18M18 6 6 18"/>',
		'chevron-down': '<path d="m6 9 6 6 6-6"/>',
		cursor: '<path d="M5.5 3.2 19 11.4l-5.7 1.2-2.5 5.3z" fill="currentColor" stroke="none"/>',
		sparkle: '<path d="M12 3l1.9 5.4L19 10.3l-5.1 1.9L12 17.6l-1.9-5.4L5 10.3l5.1-1.9z"/><path d="M18.5 15.5l.8 2.2 2.2.8-2.2.8-.8 2.2-.8-2.2-2.2-.8 2.2-.8z"/>',
		bolt: '<path d="M13.5 2 4 13.5h6L9.5 22 20 10.5h-6.5z"/>',
		shield: '<path d="M12 3l7.5 3v6c0 4.4-3.1 8.3-7.5 9.4C7.6 20.3 4.5 16.4 4.5 12V6z"/>',
		tray: '<path d="M3 13h4l1.6 2.6h6.8L17 13h4"/><path d="M5.4 4.6h13.2l2.4 8.4v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4z"/>',
		layers: '<path d="m12 3 9 5-9 5-9-5z"/><path d="m3.5 12.5 8.5 4.7 8.5-4.7"/><path d="m3.5 16.8 8.5 4.7 8.5-4.7"/>',
		move: '<path d="M12 3v18M3 12h18M12 3 9.5 6M12 3l2.5 3M12 21l-2.5-3M12 21l2.5-3M3 12l3-2.5M3 12l3 2.5M21 12l-3-2.5M21 12l-3 2.5"/>',
		'terminal-ui': '<rect x="2.5" y="4" width="19" height="16" rx="3"/><path d="m7 9.5 2.5 2.5L7 14.5M12.5 15h4.5"/>',
		'palette-ui':
			'<path d="M12 3a9 9 0 0 0 0 18c1.3 0 2-.9 2-2 0-.5-.2-1-.5-1.4-.3-.4-.5-.8-.5-1.3 0-1 .8-1.8 1.8-1.8H16a5 5 0 0 0 5-5c0-3.6-4-6.5-9-6.5z"/><circle cx="7.5" cy="12" r="1.2" fill="currentColor" stroke="none"/><circle cx="10" cy="8" r="1.2" fill="currentColor" stroke="none"/><circle cx="14.5" cy="7.5" r="1.2" fill="currentColor" stroke="none"/>',
		cpu: '<rect x="6" y="6" width="12" height="12" rx="2.5"/><rect x="10" y="10" width="4" height="4" rx="1"/><path d="M9 2.5v3M15 2.5v3M9 18.5v3M15 18.5v3M2.5 9h3M2.5 15h3M18.5 9h3M18.5 15h3"/>',
		gpu: '<rect x="2.5" y="6" width="19" height="12" rx="3"/><circle cx="8.5" cy="12" r="2.3"/><path d="M14 9.5h4.5M14 12h4.5M14 14.5h2.8"/>',
		ram: '<rect x="3" y="7" width="18" height="8.5" rx="2"/><path d="M7 7v8.5M17 7v8.5"/><path d="M6 15.5v3M9.5 15.5v3M13 15.5v3M16.5 15.5v3M20 15.5v3"/>',
		storage:
			'<rect x="3.5" y="4" width="17" height="7" rx="2"/><rect x="3.5" y="13" width="17" height="7" rx="2"/><path d="M7 7.5h.01M7 16.5h.01"/>',
		upload: '<path d="M12 16V4m0 0 4.5 4.5M12 4 7.5 8.5M4 17v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2"/>',
		box: '<path d="M20.5 7.8v8.4a1.6 1.6 0 0 1-.8 1.4l-7 4a1.6 1.6 0 0 1-1.4 0l-7-4a1.6 1.6 0 0 1-.8-1.4V7.8a1.6 1.6 0 0 1 .8-1.4l7-4a1.6 1.6 0 0 1 1.4 0l7 4a1.6 1.6 0 0 1 .8 1.4z"/><path d="m3.8 7 8.2 4.7L20.2 7M12 20.6v-8.9"/>',
		menu: '<path d="M4 7h16M4 12h16M4 17h16"/>',
		calendar: '<rect x="3.5" y="5" width="17" height="16" rx="3"/><path d="M3.5 9.5h17M8 3.2v3.4M16 3.2v3.4"/>',
		sun: '<circle cx="12" cy="12" r="4"/><path d="M12 2.5v2.5M12 19v2.5M4.6 4.6l1.8 1.8M17.6 17.6l1.8 1.8M2.5 12H5M19 12h2.5M4.6 19.4l1.8-1.8M17.6 6.4l1.8-1.8"/>',
		play: '<path d="M8 5.2v13.6L18 12z" fill="currentColor" stroke="none"/>',
		pause: '<rect x="7" y="5.5" width="3.2" height="13" rx="1.1" fill="currentColor" stroke="none"/><rect x="13.8" y="5.5" width="3.2" height="13" rx="1.1" fill="currentColor" stroke="none"/>',
		next: '<path d="M5 6.5v11l8-5.5z" fill="currentColor" stroke="none"/><path d="M15.5 6h2.3v12h-2.3z" fill="currentColor" stroke="none"/>'
	};

	const html = $derived(strokeIcons[name] ?? '');
	const isFilled = $derived(
		name === 'windows' ||
			name === 'apple' ||
			name === 'cursor' ||
			name === 'bolt' ||
			name === 'play' ||
			name === 'pause' ||
			name === 'next'
	);
</script>

<svg
	class={className}
	width={size}
	height={size}
	viewBox="0 0 24 24"
	fill={isFilled ? 'currentColor' : 'none'}
	stroke={isFilled ? 'none' : 'currentColor'}
	stroke-width={stroke}
	stroke-linecap="round"
	stroke-linejoin="round"
	aria-hidden="true"
	focusable="false"
>
	{@html html}
</svg>
