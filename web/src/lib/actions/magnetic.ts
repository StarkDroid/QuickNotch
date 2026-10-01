/**
 * Magnetic hover: the element drifts toward the cursor while it is nearby and
 * springs back on exit. Transform-only, so it never triggers layout.
 */
export function magnetic(
	node: HTMLElement,
	options: { strength?: number; radius?: number } = {}
) {
	const { strength = 0.32 } = options;

	if (
		typeof window === 'undefined' ||
		window.matchMedia('(prefers-reduced-motion: reduce)').matches ||
		window.matchMedia('(hover: none), (pointer: coarse)').matches
	) {
		return {};
	}

	let raf = 0;
	let target = { x: 0, y: 0 };
	let current = { x: 0, y: 0 };
	let animating = false;

	node.style.willChange = 'transform';

	const render = () => {
		current.x += (target.x - current.x) * 0.18;
		current.y += (target.y - current.y) * 0.18;

		const settled = Math.abs(target.x - current.x) < 0.05 && Math.abs(target.y - current.y) < 0.05;
		node.style.transform = `translate3d(${current.x.toFixed(2)}px, ${current.y.toFixed(2)}px, 0)`;

		if (settled && target.x === 0 && target.y === 0) {
			animating = false;
			node.style.transform = '';
			node.style.willChange = '';
			return;
		}
		raf = requestAnimationFrame(render);
	};

	const start = () => {
		if (animating) return;
		animating = true;
		raf = requestAnimationFrame(render);
	};

	const onMove = (event: PointerEvent) => {
		const rect = node.getBoundingClientRect();
		const dx = event.clientX - (rect.left + rect.width / 2);
		const dy = event.clientY - (rect.top + rect.height / 2);
		target = { x: dx * strength, y: dy * strength };
		start();
	};

	const onLeave = () => {
		target = { x: 0, y: 0 };
		start();
	};

	node.addEventListener('pointermove', onMove);
	node.addEventListener('pointerleave', onLeave);
	// A pointerleave can be missed if the window loses focus mid-hover, which
	// would strand the loop; reset explicitly on those events too.
	node.addEventListener('pointercancel', onLeave);
	window.addEventListener('blur', onLeave);

	return {
		destroy() {
			cancelAnimationFrame(raf);
			node.removeEventListener('pointermove', onMove);
			node.removeEventListener('pointerleave', onLeave);
			node.removeEventListener('pointercancel', onLeave);
			window.removeEventListener('blur', onLeave);
		}
	};
}
