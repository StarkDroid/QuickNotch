/**
 * Loaded through a dynamic import; Vite splits it into its own chunk so
 * the prerendered HTML stays the fast first paint.
 */

type GsapModule = typeof import('gsap');
type ScrollTriggerModule = typeof import('gsap/ScrollTrigger');

export type Motion = {
	gsap: GsapModule['gsap'];
	ScrollTrigger: ScrollTriggerModule['ScrollTrigger'];
};

let motionPromise: Promise<Motion> | null = null;

export function loadMotion(): Promise<Motion> {
	if (!motionPromise) {
		motionPromise = Promise.all([import('gsap'), import('gsap/ScrollTrigger')]).then(
			([gsapModule, stModule]) => {
				const gsap = gsapModule.gsap ?? gsapModule.default;
				const ScrollTrigger = stModule.ScrollTrigger ?? stModule.default;
				gsap.registerPlugin(ScrollTrigger);
				return { gsap, ScrollTrigger };
			}
		);
	}
	return motionPromise;
}

/** True when the visitor asked the OS to minimise animation. */
export function prefersReducedMotion(): boolean {
	if (typeof window === 'undefined') return false;
	if (document.documentElement.classList.contains('reduced-motion')) return true;
	return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
}

/** Coarse-pointer / small-screen guard for effects that are not worth the cost. */
export function isSmallScreen(): boolean {
	if (typeof window === 'undefined') return false;
	return window.matchMedia('(max-width: 768px)').matches;
}

/** Clamp helper used by the pointer-driven 3D scene. */
export const clamp = (value: number, min: number, max: number): number =>
	Math.min(max, Math.max(min, value));
