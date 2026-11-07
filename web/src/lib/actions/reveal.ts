import { loadMotion, prefersReducedMotion } from '$lib/motion/gsap';

export type RevealDirection = 'up' | 'down' | 'left' | 'right' | 'scale' | 'fade';

export type RevealOptions = {
	/** Direction the element travels in from. Must match the `data-reveal` value. */
	from?: RevealDirection;
	/** Seconds. */
	delay?: number;
	duration?: number;
	/** Extra travel distance in px, overriding the CSS default. */
	distance?: number;
	/** Reveal as soon as it mounts instead of waiting for intersection. */
	immediate?: boolean;
};

const TRAVEL: Record<RevealDirection, { x: number; y: number; scale: number }> = {
	up: { x: 0, y: 34, scale: 1 },
	down: { x: 0, y: -26, scale: 1 },
	left: { x: -34, y: 0, scale: 1 },
	right: { x: 34, y: 0, scale: 1 },
	scale: { x: 0, y: 12, scale: 0.94 },
	fade: { x: 0, y: 0, scale: 1 }
};

/**
 * Safety net for renderers that never scroll (some crawlers, print, embeds):
 * anything still hidden after this long is revealed outright so content can
 * never be permanently invisible.
 */
const REVEAL_ALL_AFTER_MS = 8000;
let safetyTimer: ReturnType<typeof setTimeout> | null = null;

function scheduleRevealAll() {
	if (safetyTimer !== null || typeof window === 'undefined') return;
	const start = () => {
		safetyTimer = setTimeout(() => {
			safetyTimer = null;
			document.querySelectorAll<HTMLElement>('[data-reveal]:not(.is-in)').forEach((el) => {
				el.classList.add('is-in', 'no-gsap');
				el.style.opacity = '1';
				el.style.transform = 'none';
			});
			// Cascading containers hide their children until the stagger runs, and a
			// fast scroll can skip an IntersectionObserver callback entirely, so they
			// need the same fallback.
			document.querySelectorAll<HTMLElement>('[data-stagger]:not(.is-in)').forEach((el) => {
				el.classList.add('is-in', 'no-gsap');
			});
		}, REVEAL_ALL_AFTER_MS);
	};
	if (document.readyState === 'complete') start();
	else window.addEventListener('load', start, { once: true });
}

/**
 * Hides the element via CSS before first paint, so there is no flash and
 * no-JS visitors still see the content.
 */
export function reveal(node: HTMLElement, options: RevealOptions = {}) {
	const {
		from = (node.dataset.reveal as RevealDirection) || 'up',
		delay = 0,
		duration = 0.85,
		distance,
		immediate = false
	} = options;

	const finishNow = () => {
		node.classList.add('is-in', 'no-gsap');
		node.style.opacity = '1';
		node.style.transform = 'none';
	};

	if (prefersReducedMotion() || typeof IntersectionObserver === 'undefined') {
		finishNow();
		return {};
	}

	let revealed = false;
	let observer: IntersectionObserver | null = null;
	let cancelled = false;

	const play = async () => {
		if (revealed || cancelled) return;
		revealed = true;
		observer?.disconnect();

		try {
			const { gsap } = await loadMotion();
			if (cancelled) return;
			const travel = TRAVEL[from] ?? TRAVEL.up;
			const dist = distance ?? Math.abs(travel.y || travel.x);
			const sign = travel.y < 0 || travel.x < 0 ? -1 : 1;

			gsap.fromTo(
				node,
				{
					opacity: 0,
					x: travel.x === 0 ? 0 : sign * dist * (travel.x < 0 ? -1 : 1),
					y: travel.y === 0 ? 0 : sign * dist * (travel.y < 0 ? -1 : 1),
					scale: travel.scale
				},
				{
					opacity: 1,
					x: 0,
					y: 0,
					scale: 1,
					duration,
					delay,
					ease: 'expo.out',
					overwrite: 'auto',
					onStart: () => node.classList.add('is-in'),
					onComplete: () => {
						gsap.set(node, { clearProps: 'transform,willChange' });
					}
				}
			);
		} catch {
			// GSAP unavailable — fall back to a CSS transition so content shows up.
			finishNow();
		}
	};

	if (immediate) {
		play();
	} else {
		observer = new IntersectionObserver(
			(entries) => {
				for (const entry of entries) {
					if (entry.isIntersecting) {
						play();
					}
				}
			},
			{ rootMargin: '0px 0px -12% 0px', threshold: 0.12 }
		);
		observer.observe(node);
		scheduleRevealAll();
	}

	return {
		destroy() {
			cancelled = true;
			observer?.disconnect();
		}
	};
}

export type StaggerOptions = RevealOptions & {
	/** CSS selector for the children to cascade. Defaults to direct children. */
	selector?: string;
	/** Seconds between each child. */
	stagger?: number;
	/** Alias for `stagger`, for terser call sites. */
	step?: number;
};

/**
 * Cascading entrance for a container's children. Children need no attributes;
 * the CSS guard `.js [data-stagger]:not(.is-in) > *` keeps them hidden until the
 * animation starts.
 */
export function stagger(node: HTMLElement, options: StaggerOptions = {}) {
	const {
		from = 'up',
		delay = 0,
		duration = 0.7,
		stagger: stepOption,
		step: stepAlias = 0.07,
		selector,
		immediate = false
	} = options;

	const step = stepOption ?? stepAlias;

	const targets = () =>
		Array.from(
			selector ? node.querySelectorAll<HTMLElement>(selector) : (node.children as unknown as HTMLElement[])
		);

	const finishNow = () => {
		node.classList.add('is-in', 'no-gsap');
		targets().forEach((el) => {
			el.style.opacity = '1';
			el.style.transform = 'none';
		});
	};

	if (prefersReducedMotion() || typeof IntersectionObserver === 'undefined') {
		finishNow();
		return {};
	}

	let revealed = false;
	let observer: IntersectionObserver | null = null;
	let cancelled = false;

	const play = async () => {
		if (revealed || cancelled) return;
		revealed = true;
		observer?.disconnect();

		try {
			const { gsap } = await loadMotion();
			if (cancelled) return;
			const travel = TRAVEL[from] ?? TRAVEL.up;
			const elements = targets();

			gsap.fromTo(
				elements,
				{
					opacity: 0,
					x: travel.x,
					y: travel.y,
					scale: travel.scale
				},
				{
					opacity: 1,
					x: 0,
					y: 0,
					scale: 1,
					duration,
					delay,
					ease: 'expo.out',
					stagger: step,
					overwrite: 'auto',
					onStart: () => node.classList.add('is-in'),
					onComplete: () => gsap.set(elements, { clearProps: 'transform,willChange' })
				}
			);
		} catch {
			finishNow();
		}
	};

	if (immediate) {
		play();
	} else {
		observer = new IntersectionObserver(
			(entries) => {
				for (const entry of entries) if (entry.isIntersecting) play();
			},
			{ rootMargin: '0px 0px -10% 0px', threshold: 0.1 }
		);
		observer.observe(node);
		scheduleRevealAll();
	}

	return {
		destroy() {
			cancelled = true;
			observer?.disconnect();
		}
	};
}
