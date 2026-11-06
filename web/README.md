# QuickNotch — marketing site

The site for QuickNotch: a bold, light, scroll-driven landing page that explains
the glanceable notch and its widgets in one tour, shows the two looks, and hands
over an installer — plus `/download` and `/changelog`, which read the release
feed the app itself uses. Free and open source, so the copy, the schema and the
installers all say the same thing.

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
| `npm run banner` | Rasterises `../docs/banner.svg` into `../docs/banner.png` for the README. |

Node 20+ is recommended (developed on Node 24).

`npm audit` reports no high-severity findings. Six low-severity advisories
remain in the build toolchain (`@sveltejs/kit` 2.x, `cookie`, `deepmerge`);
clearing them needs SvelteKit 3, which moves the config into `vite.config.ts`,
drops `$lib` and requires Vite 8 — a separate migration. None is reachable in
what ships: the output is prerendered HTML with no server, no cookies and no
form actions.

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
│   │   ├── data/releases.ts      # changelog feed + installer file names
│   │   ├── motion/
│   │   │   ├── gsap.ts           # lazy GSAP + ScrollTrigger loader
│   │   │   └── smoothScroll.ts   # Lenis, wired into the GSAP ticker
│   │   └── components/           # one component per section, plus shared primitives
│   │       ├── TourSection.svelte # the sticky scroll tour (state machine)
│   │       ├── TourDevice.svelte  # the CSS-3D laptop chassis
│   │       ├── NotchPanel.svelte  # one notch state, drawn to the app's geometry
│   │       ├── KofiButton.svelte  # Ko-fi's own button artwork, used unchanged
│   │       ├── SupportFab.svelte  # the floating support pill (every page)
│   │       ├── Spotlight.svelte  # scroll-driven glow (flat colour + blur)
│   │       └── Grain.svelte      # generated film-grain background layer
│   └── routes/
│       ├── +layout.ts            # `prerender = true`
│       ├── +layout.svelte        # fonts, styles, smooth-scroll, backdrop layers
│       ├── +page.svelte          # page composition (section order)
│       ├── download/+page.svelte # platform cards, install notes, thank-you modal
│       ├── changelog/+page.svelte# the published release history
│       ├── connect/+page.svelte  # Calendar consent (app-invoked, unlinked)
│       ├── oauth/callback/+page.svelte  # hands the code back to the app
│       └── 404/+page.svelte      # rendered to 404.html at the root of the build
├── static/                       # favicon, icons, og.png, kofi-button.png, robots.txt, sitemap.xml, manifest, _headers
├── worker/site.js                # www and http → the apex, then the assets
└── scripts/
    ├── generate-og.mjs           # SVG → PNG for the social card and icons
    ├── generate-banner.mjs       # docs/banner.svg → the README banner
    └── verify-render.mjs         # runtime smoke test over the DevTools Protocol
```

The Worker config is one level up, at the repository root (`wrangler.toml`), next
to the `package.json` whose `build` script drives this directory — see
[Hosting on Cloudflare](#hosting-on-cloudflare-quicknotchapp).

### Page order

`Hero` (`#top`) → `TourSection` (`#tour`) → `LookSection` (`#look`) →
`OfferSection` (`#download`) → `FaqSection` (`#faq`) → `Footer`.

The header is a notch, not a menu: a dark bar flush with the top edge — square
upper corners, only the two facing the page rounded, exactly the app's base
notch — carrying the mark, the wordmark, the version (linking to the changelog)
and the download button. It rests compact at the top of the page and opens to
full width as you scroll, closing again when you return to the top; the progress
hairline runs along its lower edge. There are no section links at all — the
tour, the look and the download are found by scrolling. The FAQ is the last
section; the ticket in `OfferSection` is the one call to action, and it points
at `/download` rather than shipping its own second demo.

The support ask is a floating pill in the bottom-right corner (`SupportFab`),
drawn in the same cream-and-ink as the rest of the page rather than borrowed
from Ko-fi's overlay widget: nothing is loaded from their CDN, and nothing at
all until it is clicked. It is fixed and always visible, on every page,
including `/connect` and the OAuth callback: there is no scroll threshold to
cross and no footer to step aside for. On a phone it is the cup alone.

It is the page's only standing ask. The footer carries links and the
copyright, not a second support button; Ko-fi's own artwork still appears where
it belongs to the moment, on the download ticket and in the thank-you dialog.

`/download` and `/changelog` are the only subpages anyone navigates to, and both
open the same way: a `← Home` back link above the eyebrow. The header mark, the
footer mark and the footer's `Home` also resolve to `/`, so leaving a page never
depends on guessing that the logo is a link.

The tour is the whole product in one scroll: a CSS-3D laptop settles, its lid
opens and its screen walks through the seven states — quickview, dock, system,
weather, calendar, music, gaming — with one caption each. It replaced four
separate sections (widget cards, hover demo, gaming page, theme cards) and is the
only place widgets are described.

---

## Content model

Every string, link and path lives in `src/lib/data/site.ts`. Components import
from it rather than hardcoding copy, which keeps the page copy and the JSON-LD
structured data in `Seo.svelte` provably in sync — the most common cause of
rich-result validation failures is structured data that has drifted from the
visible text.

That includes the page titles and descriptions, the OAuth status copy and the
values the mock notch draws. If a sentence appears on screen it is in this file;
components own markup, layout and punctuation between two values. User-facing
copy avoids em dashes: a colon, a full stop or a comma does the same job and
reads like a person wrote it.

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
  variables on the device (yaw, pitch, hinge angle, scale, shadow) so a frame never
  touches Svelte state. Only the step index crosses into the component, and only
  when it changes. The laptop arrives skewed and lands frontal as the lid opens;
  its base is a solid body at the hinge with the machine's real thickness, not a
  deck — so no angle ever shows keys. After a scroll
  gesture ends inside the steps region, the section glides to the nearest step
  centre — one flick advances one segment instead of flying past the tour. The
  seven captions share one grid cell, so swapping never shifts the layout, and
  all seven stay in the DOM for crawlers/AT. Reduced
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

## Download page and changelog

`/download` and `/changelog` are the only two subpages, and they share one data
source: `src/lib/data/changelog.json`, the cumulative feed the
release workflow generates. `src/lib/data/releases.ts` imports it and builds the
installer URLs from the version it names.

The feed is committed rather than fetched from GitHub for a concrete reason:
release assets are served without CORS headers, so a browser cannot read
`changelog.json` from the release itself, and going through the API would spend
a rate limit on a file the release already deploys. Importing it at build time
also means the version chip, the history and `softwareVersion` in the structured
data are in the prerendered HTML — nothing appears after hydration. The release
workflow copies the new feed to `web/src/lib/data/changelog.json` after it
publishes, which also triggers the Cloudflare build.

Installer names are stable, so no site change is needed per release:

| Platform | Asset |
| :--- | :--- |
| Windows | `QuickNotch-<version>.msi` and `QuickNotch-<version>.exe` |
| macOS, Apple silicon | `QuickNotch-<version>-arm64.dmg` |
| macOS, Intel | `QuickNotch-<version>-x64.dmg` |

Both Windows files install the same app and both self-update: the updater
prefers the `.msi` and falls back to the `.exe`, and its script branches on the
extension. The card therefore offers them as two peer buttons, the way macOS
offers two disk images, and the button labels carry the file type. Nothing sits
under the buttons on either card: the cards end where the choice ends.

Clicking any download opens a thank-you dialog once the file has started. It is a
native `<dialog>`, so focus is trapped, Escape dismisses it and the page behind is
inert without any of that being hand-rolled; it never intercepts the link, which
is already navigating by the time the card appears. The card carries Ko-fi's own
button artwork (`static/kofi-button.png`, committed rather than hot-linked) and a
"Maybe later" button. Nothing there gates the download — the tip jar is the only
thing the site asks for, and it asks after the installer is on its way.

---

## SEO

- Titles, description, canonical, robots directives, Open Graph and Twitter card
  tags, plus three JSON-LD graphs (`SoftwareApplication`, `WebSite`,
  `Organization`) rendered in the prerendered HTML. The software graph declares
  `isAccessibleForFree`, a zero price and the MIT licence, and names the
  repository in `sameAs`.
- `/download` and `/changelog` carry their own title, description, canonical and
  Open Graph tags through `PageSeo.svelte`.
- `static/sitemap.xml` lists all three public routes; `/connect` and `/oauth/*`
  stay out of it and out of `robots.txt`.
- One `<h1>` and one `<h2>` per section, with `h3`s beneath.
- Descriptive anchor text on every link, and `alt`/`aria-label` coverage.
- Every public link (site, download, changelog, source, issues, Ko-fi) comes from
  `site` in `src/lib/data/site.ts`, so the markup, the schema and the footer
  cannot point at different places.

---

## Hosting on Cloudflare (quicknotch.app)

`adapter-static` emits plain files to `build/`, all asset URLs are relative, and
there is no server rendering, no database and no secret anywhere in `web/`.
Cloudflare serves that directory as the static assets of a Worker, which is what
the repository's Cloudflare build deploys.

The Worker script (`worker/site.js`) exists for two reasons: a custom domain can
only be attached to a Worker, and the site should answer on one address.
`www.quicknotch.app` and plain `http` both redirect with a 301 to
`https://quicknotch.app`, same path, query string intact. Every other request is
handed straight to the assets binding.

The `quicknotch` Worker builds on every push to `main`:

| Setting | Value |
| :--- | :--- |
| Root directory | empty (the repository root) |
| Build command | `npm run build` |
| Deploy command | `npx wrangler deploy` |
| Environment variable | `NODE_VERSION=22` |

Nothing else about the deploy lives in the dashboard. The repository root has a
`package.json` whose `build` script hands off to `web/` (install, then Vite), and
a `wrangler.toml` that names `web/build` as the asset directory and
`web/worker/site.js` as the script. Running the two commands above from a clean
clone is exactly what the Cloudflare build does, which is also the reason the
root directory must stay empty: pointing it at `web` would hide both files.

Page counts come from Cloudflare Web Analytics: the beacon is rendered from
`+layout.svelte` with the token that lives in `site.ts`, and `_headers` allows
`static.cloudflareinsights.com` in `script-src` and `cloudflareinsights.com` in
`connect-src`, so nothing else third-party can load. It is cookieless, which is
why there is no consent banner; the desktop app itself still has no analytics at
all. `/connect` and `/oauth/*` are deliberately left uncounted: the callback URL
carries a one-time authorisation code in its query string, and that must never
reach an analytics endpoint.

`.github/workflows/deploy-web.yml` is the manual fallback: the same build, then
`wrangler deploy`. It needs two repository secrets, `CLOUDFLARE_API_TOKEN`
(Workers Scripts: Edit) and `CLOUDFLARE_ACCOUNT_ID`; without them it
type-checks and builds, then skips the deploy with a warning.

Then, once, in Cloudflare:

1. Attach the custom domains `quicknotch.app` and `www.quicknotch.app` to the
   Worker. The zone is in the same account, so both records and their
   certificates are provisioned for you; proxy stays on.
2. In the Google OAuth client, register the production redirect URI
   `https://quicknotch.app/oauth/callback` alongside the local preview URIs. The
   desktop reads the loopback port out of `state`, so no app change is needed —
   but consent fails as `redirect_uri_mismatch` until the URI is registered, and
   new URIs can take a while to propagate.
3. After the first deploy, check: `/` loads over HSTS, `_headers` took effect
   (DevTools → response headers shows `content-security-policy`), `/download`
   resolves without `.html`, `/changelog` shows the published version in its
   HTML, `/robots.txt` serves the flow disallows, `/sitemap.xml` lists only the
   three public routes, and a mistyped address returns a real 404 with the
   "this page never made it into the dock" page.

The site itself can be hosted while the repository is private, but its download
buttons cannot work: GitHub serves no release assets to anonymous clients on a
private repository. Make the repository public before pointing anyone at
`quicknotch.app`.

Security posture, all in-repo rather than in-dashboard:

- `static/_headers` sets the baseline on every response: `nosniff`,
  `DENY` framing, strict `Referrer-Policy`, a minimal `Permissions-Policy`,
  HSTS with `includeSubDomains; preload`, and a tight `Content-Security-Policy`
  (`'self'` + inline scripts/styles for SvelteKit hydration, `data:` images and
  fonts, Google OAuth endpoints in `connect-src`/`form-action` only).
  `frame-ancestors 'none'` backs the `X-Frame-Options` header. There is
  deliberately no `Cross-Origin-Opener-Policy`: the OAuth flow is plain
  top-level navigation (`location.assign`), not a popup, and a restrictive COOP
  would add failure modes without adding protection.
- `/connect` and `/oauth/*` carry `X-Robots-Tag: noindex, nofollow, noarchive`
  at the HTTP layer *and* a `noindex` meta in the markup, *and* `robots.txt`
  disallows. They are also unlinked: no nav, footer, sitemap or copy points at
  them. The only in-repo `/connect` reference is the OAuth error page's
  "Start again" link, which is itself inside the unindexed flow.
- No secrets in the repo: the Google OAuth client id and its secret are both
  injected into the desktop build at compile time and appear nowhere in this
  repository; the connect page names no client, because the desktop sends its
  own. There is no token endpoint and no cookie. The one third-party script is
  Cloudflare's Web Analytics beacon: cookieless, no identifier that follows
  anyone between sites, and the `Content-Security-Policy` allows exactly its
  origin — so there is nothing to leak and nothing to consent to.

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
download CTA**, whether the **tour advances its step and opens the lid**,
whether the hero entrance completes with each headline line at rest inside its
mask, **whether any element renders a painted gradient** (must be 0), whether the
grain texture generated, any horizontal overflow at 1440 px and 390 px, and
finally any console errors raised during interaction. The spotlight probe follows
the scroll to the download ticket's CTA.

Each probe is isolated, so one failing probe cannot abort the report.

---

## Conventions

- Svelte 5 runes only: `$props`, `$state`, `$derived`, `$effect`, snippets,
  and `onclick`-style event attributes.
- Styling is scoped per component; shared tokens and utilities live in
  `app.css`. New colours should be added as tokens rather than inline literals.
- Comments explain *why*, not *what*.
