import { browser } from '$app/environment';
import { loadMotion, prefersReducedMotion } from './gsap';

/**
 * Lenis-powered smooth scrolling, wired into the GSAP ticker so ScrollTrigger
 * and the inertial scroll never disagree about the scroll position.
 *
 * Disabled entirely for reduced-motion visitors and for touch devices, where
 * native momentum scrolling already feels better and hijacking it hurts.
 */
export async function initSmoothScroll(): Promise<() => void> {
	if (!browser || prefersReducedMotion()) return () => {};
	if (window.matchMedia('(hover: none), (pointer: coarse)').matches) return () => {};

	const [{ default: Lenis }, { gsap, ScrollTrigger }] = await Promise.all([
		import('lenis'),
		loadMotion()
	]);

	const lenis = new Lenis({
		duration: 1.05,
		easing: (t: number) => 1 - Math.pow(1 - t, 3),
		smoothWheel: true,
		wheelMultiplier: 1,
		touchMultiplier: 1.6,
		anchors: true
	});

	lenis.on('scroll', ScrollTrigger.update);

	const tick = (time: number) => lenis.raf(time * 1000);
	gsap.ticker.add(tick);
	gsap.ticker.lagSmoothing(0);

	// Expose for anchor links that live outside Lenis' own handler.
	(window as unknown as { __lenis?: unknown }).__lenis = lenis;

	return () => {
		gsap.ticker.remove(tick);
		lenis.destroy();
		delete (window as unknown as { __lenis?: unknown }).__lenis;
	};
}

/** Seamless anchor scrolling that works with or without Lenis. */
export function scrollToId(id: string): void {
	const target = document.getElementById(id);
	if (!target) return;

	// Both paths land the section's top just below the sticky header: Lenis reads
	// `scroll-padding-top` from the root itself, and the native `scrollIntoView`
	// fallback honours the same CSS, so no explicit offset is needed here.
	const lenis = (window as unknown as { __lenis?: { scrollTo: (t: Element, o?: object) => void } })
		.__lenis;
	if (lenis) {
		lenis.scrollTo(target);
		return;
	}
	target.scrollIntoView({ behavior: prefersReducedMotion() ? 'auto' : 'smooth', block: 'start' });
}
