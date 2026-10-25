# QuickNotch — marketing site

The landing page for QuickNotch: a bold, light, scroll-driven single page that
explains the glanceable notch and its widgets in one tour, shows the two looks,
and links to the installers.

It lives in the same repository as the Kotlin desktop app but is a completely
separate workspace — the app's Gradle build and the site's Node build never
interact.

---

## Why SvelteKit with prerendering

The brief was a **SPA with the best possible SEO**, which pull in opposite
directions. The setup resolves it:

| Requirement | How it is met |
| :--- | :--- |
| Real, crawlable HTML | Every route is prerendered at build time, so `build/index.html` contains the full page copy, meta tags and structured data. |
| SPA behaviour | The prerendered HTML hydrates into a client-side app; there are no full page loads and nav clicks are handled in-page. |
| Fast first paint | The critical CSS is inlined into the HTML, fonts are preloaded, and no render-blocking JavaScript is required to read the page. |
| Graceful without JS | Scroll reveals are defined in CSS behind a `.js` flag set before first paint, so with JS disabled the page is fully visible and complete. |

`adapter-static` emits plain files, so the site can be served from any static
host, a CDN, or a subdirectory (all asset URLs are relative).

---

## Quick start

```bash
cd web
npm install
npm run dev        # http://localhost:5173
```

| Script | What it does |
| :--- | :--- |
| `npm run dev` | Vite dev server with HMR. |
| `npm run build` | Prerenders and writes the static site to `web/build/`. |
| `npm run preview` | Serves the built output locally. |
| `npm run check` | `svelte-check` — types plus Svelte and a11y diagnostics. |
| `npm run verify` | Runtime smoke test against a running preview server (see [Verification](#verification)). |
| `npm run og` | Regenerates `static/og.png` and the PNG icons from SVG. Run only when those change; the output is committed. |

Node 20+ is recommended (developed on Node 24).

---

## Layout

```
web/
├── src/
│   ├── app.html                  # HTML shell; sets the `.js` flag before first paint
│   ├── app.css                   # the design system — flat tokens + utility classes
│   ├── lib/
│   │   ├── actions/
│   │   │   ├── reveal.ts         # scroll-reveal + cascade actions (IntersectionObserver → GSAP)
│   │   │   └── magnetic.ts       # magnetic hover for primary buttons
│   │   ├── data/site.ts          # ALL copy, links and not-a-token values
│   │   ├── motion/
│   │   │   ├── gsap.ts           # lazy GSAP + ScrollTrigger loader
│   │   │   └── smoothScroll.ts   # Lenis, wired into the GSAP ticker
│   │   └── components/           # one component per section, plus shared primitives
│   │       ├── TourSection.svelte # the sticky scroll tour (state machine)
│   │       ├── TourDevice.svelte  # the CSS-3D laptop chassis
│   │       ├── NotchPanel.svelte  # one notch state, drawn to the app's geometry
│   │       ├── Spotlight.svelte  # scroll-driven glow (flat colour + blur)
│   │       └── Grain.svelte      # generated film-grain background layer
│   └── routes/
│       ├── +layout.ts            # `prerender = true`
│       ├── +layout.svelte        # fonts, styles, smooth-scroll, backdrop layers
│       └── +page.svelte          # page composition (section order)
├── static/                       # favicon, icons, og.png, robots.txt, sitemap.xml, manifest
└── scripts/
    ├── generate-og.mjs           # SVG → PNG for the social card and icons
    └── verify-render.mjs         # runtime smoke test over the DevTools Protocol
```

### Page order

`Hero` (`#top`) → `TourSection` (`#tour`) → `LookSection` (`#look`) →
`PricingSection` (`#pricing`) → `FaqSection` (`#faq`) → `Footer`.

Only Tour and FAQ are in the header; the look and the price are found by
scrolling. The FAQ is the last section — there is no separate download section,
because the buy button in pricing is the one call to action.

The tour is the whole product in one scroll: a CSS-3D laptop settles, its lid
opens and its screen walks through the seven states — quickview, dock, system,
weather, calendar, music, gaming — with one caption each. It replaced four
separate sections (widget cards, hover demo, gaming page, theme cards) and is the
only place widgets are described. The ids above are the anchors the nav links
to; `Nav.svelte` derives its active-section highlighting from `nav` in
`site.ts`.

---

## Content model

Every string, link and path lives in `src/lib/data/site.ts`. Components import
from it rather than hardcoding copy, which keeps the page copy and the JSON-LD
structured data in `Seo.svelte` provably in sync — the most common cause of
rich-result validation failures is structured data that has drifted from the
visible text.

---

## Design system

`app.css` is the whole system: tokens plus a small set of utility classes.
Three rules shape it:

1. **No gradients anywhere.** Every accent is a flat colour (`--accent`,
   `--accent-text`, `--accent-soft`, `--accent-line`); panels, tiles, mock
   windows, the game backdrop, buttons and chart fills are all solid. Depth comes
   from hairlines, inset highlights and translucency instead of gradient washes —
   the one exception is a `repeating-linear-gradient` that is gone now, so the
   runtime smoke test reports **0 painted gradients**.
2. **Flat ambient background.** There is no aurora and no grid overlay. The page
   is a light base plus one flat tint, with the spotlight glow and the film
   grain layered behind the content (see below).
3. **Restrained type scale.** `--fs-mega` tops out at `6rem` and `--fs-h2` at
   `3.35rem`, so the hero is bold without taking over the page.

### Background layers

Three fixed layers sit behind the page, in this paint order:

| z-index | Element | Job |
| ---: | :--- | :--- |
| -3 | `body::before` | flat base tint, so the light base does not read as flat white |
| -2 | `Spotlight.svelte` | the moving glow described below |
| -1 | `Grain.svelte` | the film grain |

Because all three are negative `z-index`, they are genuinely background: they
never sit between the copy and the reader, and nothing needs a `pointer-events`
escape hatch beyond the layers themselves.

**The grain** is `Grain.svelte`: on mount it builds a 160×160 `ImageData` block of
full-range single-pixel noise, encodes it to a data URL and tiles it as a static
background. It is deliberately subtle — `opacity: 0.06`, no animation — so the
texture is felt rather than noticed.

**The spotlight** is `Spotlight.svelte`: two heavily blurred flat discs — flat
colour plus `filter: blur()`, *not* a `radial-gradient`, so the design system
stays gradient-free while still producing soft light. It rests near the top of
the viewport as the hero's glow, then follows the reading position down the page,
settling on one focal element per section (`.stage`, `.grid`, `.cards`, …) and
finishing on the download buttons.

Two details worth knowing before editing it:

- Target rects are read fresh each frame in a read-then-write batch rather than
  cached in document coordinates, because one target is a `position: sticky`
  panel whose document position changes while scrolling.
- The loop stops as soon as the glow has settled and scrolling has paused, and it
  is skipped entirely under `prefers-reduced-motion` (the glow is positioned once
  and left static).

---

## Animation architecture

- **GSAP + ScrollTrigger** are dynamically imported on demand (`motion/gsap.ts`),
  so they land in their own chunk and never block first paint.
- **Lenis** drives inertial scrolling and is wired into the GSAP ticker so the
  two never disagree about the scroll position. It is disabled for
  reduced-motion visitors and on touch devices, where native momentum scrolling
  is better.
- **`reveal` / `stagger` actions** handle entrance animations. They use
  `IntersectionObserver` for cheap visibility checks rather than a scroll
  handler. If the GSAP import fails they fall back to a CSS transition, and a
  page-wide safety net reveals anything still hidden after 8 seconds so content
  can never be stranded invisible.
- **`TourSection`** is a plain scroll handler rather than a ScrollTrigger pin:
  the stage is `position: sticky`, and progress is written straight to CSS
  variables on the device (rotation, hinge angle, scale, shadow) so a frame never
  touches Svelte state. Only the step index crosses into the component, and only
  when it changes. The seven captions share one grid cell, so swapping never
  shifts the layout, and all seven stay in the DOM for crawlers/AT. Reduced
  motion or no JS un-sticks the stage and shows the list with the laptop parked
  beside it.
- **The laptop is CSS 3D, not WebGL.** Its screen holds real `NotchPanel` markup,
  which keeps the mock at the app's own geometry and the text crisp; there is no
  three.js chunk to load or to fail on a machine without WebGL.
- The **spotlight** and **grain** (see [Design system](#design-system)) provide
  the environmental depth. The spotlight reads layout in one batched pass per
  frame and stops once settled; the grain is a single static layer, so it costs
  nothing after first paint.

---

## Performance notes

Measured from a production build:

| Asset | Size | gzip | On the critical path? |
| :--- | ---: | ---: | :--- |
| `index.html` (incl. inlined critical CSS) | 71 kB | 15.7 kB | yes |
| Page CSS | 53 kB | 12.2 kB | yes |
| Page + entry JS | 151 kB | 59.4 kB | yes |
| GSAP + ScrollTrigger chunk | 115 kB | 45.4 kB | no — loaded on mount/idle |

There is no renderer chunk: the hero was previously a three.js scene, and dropping
it removed a 510 kB (129 kB gzip) lazily-loaded chunk as well as the WebGL risk on
transparent overlays. The hero's visual weight now comes from the spotlight and
the live widget notch in the mock screen.

Other deliberate choices:

- Fonts are self-hosted with `unicode-range`, so a Latin-only visit fetches the
  Latin subsets only, and the two Latin files are `<link rel="preload">`ed to
  bring the LCP headline in sooner.
- Only `transform` and `opacity` are animated.
- Every animation loop is torn down in an `$effect` return, and every
  `IntersectionObserver` / `ResizeObserver` is disconnected.

---

## SEO

- Titles, description, canonical, robots directives, Open Graph and Twitter card
  tags, plus three JSON-LD graphs (`SoftwareApplication`, `WebSite`,
  `Organization`) rendered in the prerendered HTML.
- `static/sitemap.xml` and `static/robots.txt`.
- One `<h1>` and one `<h2>` per section, with `h3`s beneath.
- Descriptive anchor text on every link, and `alt`/`aria-label` coverage.
- The visible CTAs all point at `site.purchaseUrl`; `site.downloadUrl` survives
  only in the `SoftwareApplication` schema. There is no code-host, licence or
  issue-tracker reference anywhere in the markup, the copy, the structured data
  or the generated Open Graph image.

### Before deploying

1. **Set the purchase destination.** Every visible CTA reads `site.purchaseUrl`
   in `src/lib/data/site.ts` (`https://quicknotch.app/buy` as a placeholder).
   `site.downloadUrl` is schema-only now; point it at the installer or drop the
   property.
2. **Confirm the domain.** `site.url` drives the canonical URL, the Open Graph
   image URL, the sitemap and the JSON-LD. Update it alongside
   `static/sitemap.xml` and `static/robots.txt`.

---

## Accessibility

- Landmarks throughout (`header`, `main`, `footer`, one `<section id>` per
  topic), a skip link, and visible `:focus-visible` rings everywhere —
  including a `:focus-within` ring on inputs whose native outline is removed.
- The tour is content, not a control: all seven captions are in the DOM (only
  their pixels swap), each is a real heading, and the device and its screen are
  `aria-hidden` because the caption carries the meaning. There are no buttons to
  tab through mid-scroll.
- `prefers-reduced-motion` disables Lenis, the spotlight tracking and the scroll
  scrub; the tour becomes a static device plus a plain list.

---

## Verification

`svelte-check` covers types, Svelte diagnostics and a11y lint. For everything a
static build cannot prove, use the runtime smoke test:

```bash
npm run build
npx vite preview --port 4173 &
node scripts/verify-render.mjs http://127.0.0.1:4173/
```

It drives headless Chrome over the DevTools Protocol (no extra dependencies)
and reports, in order: **page health** (uncaught errors and console errors,
reported first so a hydration crash is obvious), whether the reveal actions fire,
whether the **spotlight tracks the scroll and lands within a few pixels of the
pricing CTA**, whether the **tour advances its step and opens the lid**,
whether the hero entrance completes with each headline line at rest inside its
mask, **whether any element renders a painted gradient** (must be 0) and whether
the grain texture generated, any horizontal overflow at 1440 px and 390 px, and
finally any console errors raised during interaction.

Each probe is isolated, so one failing probe cannot abort the report.

---

## Conventions

- Svelte 5 runes only: `$props`, `$state`, `$derived`, `$effect`, snippets,
  and `onclick`-style event attributes.
- Styling is scoped per component; shared tokens and utilities live in
  `app.css`. New colours should be added as tokens rather than inline literals.
- Comments explain *why*, not *what*.
