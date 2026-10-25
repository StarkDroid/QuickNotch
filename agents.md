# AGENTS.md — QuickNotch

> Onboarding doc for a fresh agent. Read this first, then verify against code —
> the file a comment narrates is the source of truth. Keep this doc honest:
> update it in the same commit when a fact changes.

## 1. What this is / what we're building

**QuickNotch** is a floating notch-and-dock for **Windows 10/11** and **macOS 12+**:
a small pill pinned top-centre. Hover → expands into a dock of pinned apps
(fisheye magnification, launch on click) plus widget pages reached by wheel.

- **Collapsed pill (QuickView):** live-media branch (vinyl disc, marquee title,
  optional waveform analyser, volume + device-battery rings), idle-clock branch
  (date/time + weather chip + laptop-battery ring + notification bell), idle dots,
  drag-handle style. Content-hugging: clock/dots shrink to measured width;
  live media fills the configured width.
- **Expanded pages (wheel order):** Music (when live) → Apps → System (when on) →
  Weather → Calendar. Music page: vinyl + title/artist—album + progress + transport
  + rings. Calendar page: schedule view + ticker. Weather page: today + 7-day.
  System page: one `LevelRing` per metric (glyph inside, live value under it) —
  CPU, GPU (only where the OS reports an engine), memory, storage, download, upload.
- **macOS placement:** island sits flush at the true top of the screen, above the
  menu bar (`NSStatusWindowLevel` 25, joins all Spaces, over full-screen apps).
  Unreachable via AWT — an ObjC dylib parks it there.
- **Presence rule:** quiet agent while only the notch runs (macOS accessory policy
  = no Dock icon/menu; Windows `WS_EX_TOOLWINDOW | WS_EX_NOACTIVATE` = no
  taskbar/Alt+Tab/focus steal). Dock icon + activation appear **only while the
  Settings window is open**. Manual launch opens Settings; `--quicknotch-launched-at-login`
  boot stays tray-quiet.
- `web/` is a separate static SvelteKit marketing site (also hosts
  `/connect` + `/oauth/callback` for Calendar OAuth). Ignore unless asked.

## 2. Stack & KMP integration (`gradle/libs.versions.toml`)

| Thing | Version / note |
| --- | --- |
| Kotlin / CMP | 2.1.10 / Compose Multiplatform 1.7.3 (Desktop JVM), Material3, `materialIconsExtended` |
| Coroutines | core 1.10.1 + swing (AWT/EDT == render thread) |
| Serialization / HTTP / dates | serialization-json 1.7.3; **Ktor 3.1.3 CIO** + kotlinx-datetime 0.6.2 |
| Native | JNA 5.16.0 + jna-platform (Win32); PowerShell 5.1 bridges; macOS ObjC bridges via `cc` |
| Renderer | **Skiko 0.8.25 pinned** (whole `org.jetbrains.skiko` group). macOS METAL, Windows DIRECT3D |
| Build | plain KMP `jvm("desktop")`; no Android/iOS; detekt 1.23.8; `slf4j-nop` silences Ktor warnings |

**Source-set boundary (load-bearing):**

- `commonMain` = pure Kotlin only (calendar `CalendarApi`, weather `WeatherApi`).
  No `java.*`, no `AppLog`. HTTP must be **Ktor CIO** — the jpackage runtime is a
  jlink image with ~7 modules (`java.base/desktop/logging/prefs/xml`…); there is
  **no `java.net.http`, no `jdk.httpserver`**. Anything needing those crashes only
  in the installed app (`NoClassDefFoundError`, an `Error` — catch `Error` too).
- `desktopMain` = everything platform: `main.kt`, `datasource/`, `media/`,
  `calendar/GoogleCalendar`, `weather/Weather`, `system/` (SystemMetrics +
  per-platform probes), `ui/`, `model/`.
- OAuth loopback is a hand-rolled `ServerSocket` (single GET), never `HttpServer`.
- Resources: strings in `commonMain/composeResources/values/strings.xml` → `Res.string.x`;
  glyphs in `drawable/*.svg` (solid `fill="currentColor"` silhouettes, tinted SrcIn).
  **Each `Res` accessor needs its own explicit import** (`...generated.resources.<name>`).
- Skiko: never set `skiko.renderApi` unconditionally (shadows env + marker file).
  Order: `-Dskiko.renderApi` / `SKIKO_RENDER_API` → `software-render` marker file in
  `AppPaths.configDir` → METAL default. Log line `skiko render backend = …` confirms.

## 3. Commands

```bash
./gradlew :composeApp:compileKotlinDesktop  # fast check (gradlew.bat on Windows)
./gradlew :composeApp:desktopJar            # full jar, also syncs/compiles bridges
./gradlew :composeApp:run                   # launch (overlay + tray); NOT java -jar (no manifest)
./gradlew :composeApp:detekt                # must be green before commit
./gradlew :composeApp:packageDistributionForCurrentOS  # .msi/.exe or .dmg
SKIKO_RENDER_API=SOFTWARE ./gradlew :composeApp:run    # one-off rasterizer A/B
```

Bridge tasks (`composeApp/build.gradle.kts`, auto-run by jar/run): `syncWindowsBridge`
(`native/windows/*.ps1` → resources; edit source, never the copy),
`compileMacNowPlayingBridge` / `compileMacBtWatchBridge` / `compileMacPassthroughBridge`
/ `compileMacArtworkBridge` + `syncMacArtworkDriver` (macOS hosts only; absent resource =
controller stays silent by design). Kill a run via the `MainKt|quicknotch` java process.
Install a DMG with `rsync -a` (`cp -R`/`ditto` leave empty bundles).

## 4. Architecture

### 4.1 Process & windows (`desktopMain/.../main.kt`)

One `application {}`; `FavoritesRepository` + `MediaController` singletons.
Overlay = undecorated, transparent, `alwaysOnTop`, top-centred.
Settings = normal 880×660 window (`SettingsScreen`). Tray / menu-bar extra: show/hide,
Always-on-top toggle (mirrors Behavior tab), Settings, reset, quit.
Pollers on `Dispatchers.IO`, UI observes `StateFlow`s on EDT. `OverlayPager` is touched
from the AWT wheel listener and composition on the same thread — no locks.

- **Fixed footprint, drawn growth.** The native window keeps one size (expanded dock
  size) for life; open/collapse is drawn island growth inside it. Never resize the
  transparent window per open/close (macOS white-flash/frame-skip). Position atomically
  (`LaunchedEffect(config.style, targetSize)`); never re-place from live window dims.
- **macOS above-menu-bar** (`MacAppKit` + `passthrough_bridge.m` dylib, main-queue
  marshalled): level 25 + `canJoinAllSpaces|stationary|fullScreenAuxiliary|ignoresCycle`
  + top-gap `edgeGapDp`. Always-on-top off → level 0 below menu bar. ~700 ms reconcile
  loop re-asserts (AWT/Compose re-parks 30pt low). AWT `alwaysOnTop` only reaches level 3
  and clamps y ≥ menu height — never use it for placement/math on macOS.
- **Dock presence** (`MacDockPresence`): accessory alone, Regular + one-shot
  `activateIgnoringOtherApps` on Settings open, back to accessory on close. Reconcile
  re-asserts **policy only**. Start/drive from `DisposableEffect`/`LaunchedEffect`,
  never the composable body (body re-runs per media tick → focus steal + dead wheel).
- **Passthrough** (`WindowPassthrough`): clips the touch target to the **drawn island**.
  Publish inline in `onGloballyPositioned` (the `LayoutCoordinates` instance is stable —
  a keyed effect runs once and freezes the target). `localToWindow` is already physical
  px — never multiply by density again. Windows: `SetWindowRgn` (+2px inflate, EDT).
  macOS: cursor-gated `setIgnoresMouseEvents:` off-EDT (IO worker; EDT→main-queue
  `dispatch_sync` at 33 Hz deadlocks) + delayed synthetic MOUSE_MOVED re-arm. Fail-soft:
  handle/bridge missing → fully interactive. Native handle via reflected AWT peer +
  `--add-opens` (`java.awt`, `sun.lwawt[.macosx]` / `sun.awt.windows`, host-gated).

### 4.2 Overlay: geometry, hover, pager, morph

- `OverlayGeometry.kt`: `computeNotchMetrics` + `DockLayout` own footprints.
  Island depth = `expandedNotchHeightDp`(70–86, default 78) + 6. Widths: music/weather/
  system pages 356dp, live-collapsed 236dp, clock/dots hugged (floor 32dp + 16dp pad).
  `calendarConnected`/`weatherReady`/`systemReady` gate those pages and the width, so a
  page with nothing behind it never becomes a dead stop on the wheel.
  Indicator slots (+28dp each: device + laptop battery, notification bell, weather = 2)
  come from `quickViewIndicatorSlots(...)` — **both** the window sizer and content must
  use it or the right icon clips.
- `FloatingNotchOverlay.kt` (root: choreography only) + `OverlayHover.kt`
  (`cursorOverNotch` real-cursor check; gate only suppresses collapse; re-poll until
  confirmed out — one-shot wedges open) + `NotchMotion.kt` (springs/timings,
  `PillAnimationFps` 30) + `OverlayPager.kt` (page/wheel state).
- Pager: Music-first while live, snap to 0 on collapse, one page per swipe burst
  (momentum swallowed; re-arm only after ~100 ms quiet). Windows = AWT
  `MouseWheelListener` (Compose never sees scroll on the unfocused overlay);
  macOS = Compose `Scroll` event. Never both (double-count). Synthetic scroll never
  reaches the overlay on macOS — test paging with a real device.
- Morph (`NowPlayingMorph.kt`): `SharedTransitionLayout`, keys disc/title/volume/
  battery. **Always `sharedElement`, never `sharedBounds`** — bounds renders both
  copies mid-flight (two covers/titles). Pill stays visible until `showDock` when live
  (`if (musicLive) !showDock else !isExpanded`); idle uses the curtain (quickview slides
  down, dock scales up from 0.82 behind it). Animated island size is `State<Dp>` read
  **only in the layout phase** (`Modifier.animatedIslandSize`) + `snapshotFlow` to the
  blur bridge — reading it in composition recomposes the whole tree per spring frame.
- Inactive pages must not compose pointer modifiers at all (`.then(if (interactive) …)` /
  `onClick = null`); `enabled = false` is still hit-testable and blocks pages beneath.

### 4.3 Media, rings, calendar/weather/notifications

- **Media** (`media/`): one `StateFlow<MediaSession?>`, one platform controller, JSON
  wire `{state,src,srcId,title,artist,album,pos,dur,art,id}`. `publish()` extrapolates
  pinned browser positions, caches art per content id, decodes **straight to ≤256px**
  (`sourceSubsampling`) + derives `artTint` once on the worker. 4 s empty-grace hold
  ends on a **timer** (`armHoldExpiry`), not the next line (bridge de-dups); all writes
  via `publishLock`. Local 250 ms `nowMs` clock lives in the progress composable only.
  Windows `smtc.ps1` (seek OK; `status <id> <haveArt>` skips re-transfer —
  three-valued gate '0' wanted / '1' held / '2' tried-empty so artless sources are
  asked once; stderr merged so one stream drains; 4 s deadline then
  `destroyForcibly` so a winRT wedge frees the reader). macOS
  `nowplaying-bridge` → `osascript -l JavaScript` (only `com.apple.*` contexts reach
  MediaRemote ≥15.4; no seek). Art bytes **once per track** (`EmitArtOnce`, else null);
  drain large stdout **during** `waitFor` (pipe buffer), small lines after. Late art via
  `publishArtwork` (identity-gated, stale dropped).
- **Artwork (macOS):** JXA never returns bytes (entitlement). `MacArtworkFetcher` runs
  `artwork-driver.pl` + `artwork-bridge.dylib` inside `/usr/bin/perl` (one query per
  artless track). Music AppleScript fallback gated to `srcId com.apple.Music` only.
- **Rings** (`LevelRing.kt`, 288° arc, 20% bottom gap, takes a `Painter`; 22dp pill /
  28–30dp page): `SystemVolume` (2 s, one spawn `<pct>,<muted>`; page ring = mute toggle,
  pill ring display-only), `AudioBattery` (WinRT presence + `DEVPKEY` level; macOS
  `system_profiler` SPBluetooth 10 s + CoreAudio `btwatch-bridge` event poke; audio-class
  + default-output only, no BLE ghosts), `SystemBattery` (`pmset`/`Win32_Battery`, 5 s,
  `hasBattery` gates toggle, charging = active only), `SystemAccent` (3 s while picked).
  Failed probe keeps last value; null hides. Every spawn: `waitFor(timeout)` +
  `destroyForcibly`. Bridges arm `getppid()==1` suicide + startup
  `sweepOrphanedBridges()` (leaked bridges poll MediaRemote forever → machine stalls).
- **Calendar** (`calendar/`, Ktor CIO in `commonMain`): Web-client OAuth via site
  (`/connect` → Google → `/oauth/callback` → loopback; `client_secret` mandatory;
  `state` = base64url `{port,nonce}` verified exactly; top-level navigation, not fetch;
  `/connect` has no marketing Nav, `Seo` takes no props). `timeMin/timeMax` are RFC3339,
  not epoch. Check `status.isSuccess()` before parsing (refusal ≠ empty day); sealed
  `EventsResult`/`OAuthResult`; connected ≠ day-synced (`staleError`); refresh under mutex;
  derived ticker loop starts unconditionally. Testing consent needs test-user status +
  callback URI registered (propagation can take hours).
- **Weather** (`weather/`, `WeatherApi` commonMain): IP-geolocate + Open-Meteo, keyless,
  metric feed, °F as render conversion. Half-hourly poll + `weather.json` cache;
  Ready/Loading/Failed; page gated like calendar (no cache + failed = no page).
  Optional manual place (`WidgetsConfig.weatherPlace`, null = automatic) picked from
  Open-Meteo geocoding search in the Widgets tab; `Weather.setManualPlace` applies it
  live, `start(place)` seeds it at boot.
- **Notifications (macOS):** `count(*)` on usernoted `record` (WAL-aware: fingerprint
  db/-wal/-shm at 250 ms + 3 s backstop). Needs Full Disk Access (settings deep-links
  the pane, re-pokes on focus); unreadable → hidden. Windows has no store → hidden.
- **System monitor** (`system/`, `WidgetsConfig.showSystem`, default on): one
  `StateFlow<SystemSnapshot?>`, poll **gated on the toggle** (`SystemMetrics.setEnabled`
  → a switched-off widget spawns nothing and clears the snapshot). macOS runs exactly
  one `/bin/sh` per 2 s tick (`sysctl hw.ncpu hw.memsize`, `ps -A -o %cpu=`, `vm_stat`,
  `netstat -ibn`, `df -k /System/Volumes/Data`, `ioreg -c IOAccelerator`) ≈ 40 ms;
  Windows runs one `system-metrics.ps1` per 4 s tick (`Get-Counter` PDH over a 1 s
  sample for CPU/memory/GPU-engine/network, `DriveInfo` for the disk). Static facts
  (total RAM, GPU name via `system_profiler` ≈ 300 ms) are read **once per run**, never
  per tick. Readings: CPU = Σ`ps %cpu` ÷ cores; memory = active + wired + compressor;
  storage = the **data** volume (the read-only system volume reads ~14% falsely);
  GPU = highest `Device Utilization %` (macOS) / summed 3D-engine utilization clamped
  to 100 (Windows) — no counter means no GPU tile (`gpuAvailable`). Throughput comes
  from cumulative counters, so **both directions must share one delta** and the first
  sample is always null. `emit()` refuses a sample that lands after the switch-off, or
  the settings row shows a frozen reading forever. Load metrics warm to `LoadHighTint`
  ≥ 85; network rings use a log sweep (1 KB/s → 0 … 1 GB/s → 100) and the label carries
  the exact rate.
- **Persistence:** `config.json` + `favorites.json` (+ `calendar.json`, `weather.json`)
  in `%APPDATA%/QuickNotch` / `~/Library/Application Support/QuickNotch`.
  `@Serializable` defaults (old files load); coalesced single-writer; `resetToDefaults`
  keeps `onboardingDone`.
- **Blur:** `NotchSurface.TRANSLUCENT` (= serialised `BLUR`) = native blur + 18% scrim.
  macOS = standalone island-sized helper panel below the overlay (never an AWT child —
  CGS radius silently no-ops there), 250 ms frame/level/order/visibility sync;
  `CAShapeLayer` masks (not `cornerRadius`), no rim (`blurScrim.border` transparent);
  intensity drives radius + opacity. Windows has no usable compositor route — all
  four were probed live against a striped backdrop: DWM blur-behind composites
  behind the window (or is ignored on a layered one), `DWMWA_SYSTEMBACKDROP_TYPE`
  acrylic draws a solid rect ignoring region + transparency,
  `SetWindowCompositionAttribute` acrylic ignores `SetWindowRgn`, and
  `WDA_EXCLUDEFROMCAPTURE` blacks a layered window's whole rect in every GDI
  capture. So the frost IS a screen capture: a ~30 fps daemon (`BitBlt`
  SRCCOPY|CAPTUREBLT → `GetDIBits` top-down 32bpp, clamped to the virtual screen,
  inflated 3σ past the island) **inpaints the self-drawn hole** (island rect +
  5dp ring pad, replaced by the average of a horizontal lerp between the flanking
  bands and a vertical blend between the rows above/below — no self-baking
  ghost), runs a **CPU Gaussian** (3-pass box cascade, clamp-to-edge; sigma =
  `(4 + 0.5×intensity)dp` — the slider drives an exact radius), crops to the
  island rect and publishes an `ImageBitmap` to a `StateFlow`. The `WindowsFrost`
  leaf inside the island Box draws it 1:1 under the legibility scrim, its own
  recompose scope (a frame tick touches nothing else); the painted body skips
  via `frostActive`. Capture is inflated 3σ so the rim's whole sample window is
  real backdrop; the flush screen-top edge has nothing above to sample and gets
  the reconstruction (invisible under the Gaussian). No exclusion flag — the
  notch stays in screenshots/screen sharing. Intensity is still also the scrim
  alpha. Missing bridge / any native failure → last frame or painted glass.
  Single-instance file lock in `main()`.

### 4.4 UI modules (`ui/`)

Root choreography `FloatingNotchOverlay`; pill branches + strip `QuickView`;
pages + fisheye dock `DockContent`; vinyl/marquee/progress/transport/waveform
`MediaWidgets`; morph keys `NowPlayingMorph`; hover `OverlayHover`; springs/timings
`NotchMotion`; metrics `OverlayGeometry`; pager `OverlayPager`; rings `LevelRing`;
clock/bell `QuickViewClock`; surfaces `IslandSurface`; skeleton `Shimmer`;
`CalendarWidget`, `WeatherWidget`, `SystemWidget` (System page: six ring tiles,
`systemRate`/`systemRateLevel`), `OnboardingScreen` (gates overlay on
`onboardingDone`), `PinnedAppsPanel`, settings `SettingsScreen`/`WidgetsTab`/
`DockSizingTab`/`BehaviorTab`/`SettingsComponents` (shared chrome + `plainClickable` /
`trackHoverPress` helpers; tabs collect their own state — screen collects none).

## 5. Design system

Palette in `theme/Color.kt` (+ overlay-local `LiveAccent` etc. in `MediaWidgets.kt`).
Settings/onboarding are gradient-free solids. Glyphs = tinted SVGs only.
Reusable `LevelRing` (one ring, several alert rules: `lowTint`/`lowThreshold` for a
draining battery, `highTint`/`highThreshold` for a saturating load metric) /
`VinylDisc` (spins 12 s/turn while playing, frozen angle on pause;
page swap = `AnimatedContent` keyed on the bitmap, clipped at island) / `MarqueeText`
(scroll iff overflowing) / `TrackProgress` (inset lane, head iff seekable).
Waveform = faux analyser (2 sines/band + shared envelope, 8–40 bars by width, white or
`artTint`), 30 Hz `delay` + `nanoTime` deltas, pause ramps to a flat line then clock stops.
**Animation rules:** `delay`-ticks at ≤30 Hz, never `withFrameNanos`; move pixels via
`graphicsLayer`, never `Modifier.offset{}`; kill transitions whose visual is static;
resolve draw-table sizes in composition, never from `onSizeChanged` writes.

## 6. Rules — DO / DON'T

DO: strings → `strings.xml`; colors → `Color.kt`; glyphs → `drawable/*.svg`;
follow file-local `//`-narration style; read the file before editing; reuse
ring/vinyl/marquee/progress + settings helpers; gate pollers on `HostPlatform`,
failed probe keeps last value; high-frequency state collected in the drawing subtree,
frame clocks local to their composable; monotonic clocks for extrapolation.
Verify: `compileKotlinDesktop` → `desktopJar` → `:composeApp:run` → `:composeApp:detekt`.

**Every feature ships on three surfaces — app, onboarding, web.** A new widget,
toggle or interaction is not finished until (a) it works in the overlay, (b) the
onboarding walkthrough can show it or set it where a first-run user would meet
it, and (c) `web/` tells the same story: the demo notch paints the same widget
with the same layout and labels, and the copy, feature list, keywords, FAQ and
structured data name it. Land all three in the same commit. A marketing demo of
a widget we do not ship — or an onboarding step describing one we removed — is a
bug, not a nicety. When the demo needs a visual the app has, match the app's
geometry (ring sweep, capsule shapes, dot sizes) rather than inventing one.

DON'T: force SOFTWARE renderer; set `skiko.renderApi` unconditionally; edit
`resources/windows/*.ps1` (edit `native/windows/`); `java -jar` the plain jar; resize the
overlay per open/close; re-place from live window dims; derive placement/math from
`locationOnScreen`/`localToScreen` without the CG-origin delta; `activateIgnoringOtherApps`
in a loop; native side effects in composable bodies; `dispatch_sync` to AppKit from EDT;
`sharedBounds` for same-content morphs; `enabled=false` for page input gating; second wheel
path on Windows; static WinRT stream-extension calls in `smtc.ps1` (pre-bind the
MethodInfo — see §7); per-tick persistence (repo coalesces); new strings/colors/glyphs inline;
`String.format` assumptions on `stringResource` (`%1$s` bare substitution — `%%` doubles);
scope-creep (new notch positions, Android/iOS) unless asked.

## 7. Critical gotchas (distilled — full stories were in §10)

- BT battery: macOS = `system_profiler` parse (plist DeviceCache is dead) + default-output
  match; events from CoreAudio watcher, not bluetoothd; 10 s poll + `poke()`.
- Glyphs must be solid silhouettes (`fill-rule="evenodd"` for holes); strokes tint to ~1/3.
- Art: ≤256px subsample; bridge sends bytes once per id; Music fallback only for Music.
- SMTC art on Windows: the thumbnail stream is a raw `System.__ComObject`, and
  PowerShell 5.1 cannot bind it to a static `WindowsRuntimeStreamExtensions`
  method's `IInputStream` parameter ("Cannot find an overload" — its conversion
  reads `GetInterfaces()`, which is empty on a COM RCW). Pre-bind the MethodInfo
  and `Invoke` it: the CLR cast QIs and succeeds.
- Windows blur: no DWM route shapes frost on a layered window (blur-behind
  composites behind / is ignored; both acrylics ignore region + transparency;
  `WDA_EXCLUDEFROMCAPTURE` blacks a layered window out in every GDI capture) —
  capture + CPU-Gaussian + inpaint the self-drawn hole instead, or the island
  ghosts into its own backdrop. Inflate the capture 3σ (the rim's whole sample
  window must be real backdrop) and clamp the BitBlt to the virtual screen
  (the flush-top island always pokes above y=0; an unclamped blit fails
  silently, every frame). `GetDIBits` needs the bitmap deselected first, and
  32bpp BI_RGB alpha is undefined — force opaque.
- Progress outline straddles the island edge from a padded wrapper sibling
  (the island clip would cut its outer half); pad is 0 when the ring is off.
- Pipe: small output read-after-`waitFor`; multi-100KB base64 drained concurrently.
- System monitor (macOS probe): `netstat` **must** be `-n` (without it every interface
  address resolves through DNS and the probe blocked ~5 s); `netstat -ib` repeats a row
  per address family and only the first carries the counters (de-dupe by interface,
  skip `lo0`); `df` the `/System/Volumes/Data` volume (root reads ~14% falsely);
  `ioreg -c IOAccelerator` gives GPU load only where the driver prints
  `Device Utilization %`. On Windows the PDH sample itself costs ~1 s, so the poll is
  4 s and RAM total / GPU name come from one `-Static` run.
- Calendar: RFC3339 times; 403 body names a disabled API; Testing clients need test users;
  loopback port must be `--strictPort`-stable and the URI identical in app + page + console.
- macOS `prev` restarts the track when position > ~3 s — verify via position, not title.
- Detekt 1.23.8 gates new code (`UnusedImports` on); old findings in `baseline.xml`,
  re-cut with `detektBaseline` only after deliberate fixes; tunings: `MagicNumber` off,
  `@Composable` exempt from `FunctionNaming`.
- `git status` before any `stash` (shared branch); never push (user pushes); signed commits.

## 8. Git workflow & scope

Branch `main` (older work: `cursor/macos-compose-adaptation`). One logical change per
commit, imperative summary, GPG-sign (`git commit -S`), author stays the user, never push.
Before commit: compile + jar + smoke run + detekt green + resource sweep (unused
`Res.*`, zero `Brush.*Gradient` in settings/onboarding) + web/onboarding parity
(§6) — app, onboarding and `web/` in the same commit. End with clean `git status`;
note unverifiable paths honestly (macOS-only paths have no machine here: CGS/panel
blur, passthrough bridge, menu-bar presence; needs live eyes: morph smoothness,
waveform feel, blur look).
