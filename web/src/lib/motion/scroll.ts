/** Anchor navigation with the header offset from `scroll-padding-top`. */
export function scrollToId(id: string): void {
	document.getElementById(id)?.scrollIntoView({ block: 'start' });
}
