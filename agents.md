# AGENTS.md — QuickNotch

Onboarding doc for a fresh agent/session. Read this first, then verify anything you
touch against the code — the file the comment narrates is the source of truth.

---

## 1. What this project is

**QuickNotch** is a Dynamic-Island-style floating dock for **Windows 10/11** and
**macOS 12+**: a small pill pinned to the top-centre of the screen. Hover it →
it expands into a dock of pinned apps (fisheye magnification, launch on click),
plus a growing set of **widgets**: a now-playing "live activity" (vinyl disc,
marquee title, volume + device-battery level rings, transport controls, seekable
gradient progress bar) and an idle clock (date/time) in the quickview.

One Kotlin/Compose Multiplatform (Desktop JVM) codebase runs both platforms;
only scanning, icons, launch, login-items, and the media/battery bridges differ
(Win32/JNA + PowerShell vs. macOS `open`/`sips`/`plutil`/`pmset`/Objective-C).

The repo also contains `web/` (a separate SvelteKit marketing site) — unrelated
to the app build, ignore it unless explicitly asked.

---

## 2. Repo fast facts

- Branch: `cursor/macos-compose-adaptation` (all recent widget work lands here).
- App-code HEAD at doc creation: `0c9a9a7` — "Unify vinyl disc, stack rings over transport, morph title by scale, restyle progress". (This doc and its commits sit on top.)
- No test source set exists. Verification = **compile + desktop jar + manual/visual smoke run**.
- Gradle wrapper (`gradlew.bat`), JDK 17+, run from `G:\QuickNotch`.
- Locale strings live in `composeApp/src/commonMain/composeResources/values/strings.xml` and are referenced as `Res.string.x` via `stringResource(...)`.
- Icons/glyphs live in `composeApp/src/commonMain/composeResources/drawable/*.svg` (tinted at draw time via SrcIn).

---

## 3. Stack & versions (`gradle/libs.versions.toml`)

| Thing | Version/Notes |
| --- | --- |
| Kotlin | 2.1.10 |
| Compose Multiplatform | 1.7.3 (Compose Desktop), Material 3, `materialIconsExtended` |
| Concurrency | kotlinx-coroutines-core 1.10.1 + kotlinx-coroutines-swing (AWT/EDT == the Compose render thread) |
| Serialization | kotlinx-serialization-json 1.7.3 |
| Native | JNA 5.16.0 + jna-platform (Win32), PowerShell 5.1 bridges, macOS ObjC MediaRemote bridge |
| Renderer | Skiko default (DIRECT3D on Windows); **SOFTWARE is forced at runtime on macOS only** |
| Build | plain KMP `jvm("desktop")` target; no Android/iOS |

---

## 4. Getting started

```bash
cd G:\QuickNotch
.\gradlew.bat :composeApp:compileKotlinDesktop   # fast compile check
.\gradlew.bat :composeApp:desktopJar              # full jar (also syncs native bridges)
.\gradlew.bat :composeApp:run                     # launch the app (overlay + tray)
.\gradlew.bat :composeApp:packageDistributionForCurrentOS   # .msi/.exe or .dmg
```

**Smoke-test with `:composeApp:run`, never `java -jar`**: the plain desktop jar
carries no main manifest. To stop a `run` session, kill the `java` process whose
command line matches `MainKt|quicknotch`.

`desktopJar`/`desktopProcessResources` depend on two custom tasks (see
`composeApp/build.gradle.kts`):

- `syncWindowsBridge` — copies `composeApp/native/windows/*.ps1` → `src/desktopMain/resources/windows/`.
- `compileMacNowPlayingBridge` — builds `native/macos/nowplaying_bridge.m` with `cc` (macOS hosts only).
- `compileMacBtWatchBridge` — builds `native/macos/btwatch_bridge.m` with Foundation + CoreAudio (macOS hosts only).

If a Windows `.ps1` script is edited in `native/windows`, the copy into
`resources/windows` is refreshed automatically at jar time — but **during a plain
`run` the app reads the copied resource**, so keep the two in sync.

---

## 5. Architecture

### 5.1 Process & windows (`main.kt`)

- One `application {}`; `FavoritesRepository` + `MediaController` are singletons owned here.
- **Overlay window**: undecorated, transparent, `alwaysOnTop`, non-resizable, top-centred. Its native window is explicitly re-sized and **atomically repositioned** (size+position written together in one `LaunchedEffect`) after the AWT window maps — this keeps the notch perfectly still while growing/shrinking. `LaunchedEffect(config.style, targetSize)` handles placement; never re-place on the window's own live dimensions (feedback loop).
- **Tray** (Windows) / menu-bar extra (macOS): show/hide notch, open Settings, reset defaults, quit.
- **Settings window**: 880×660, normal window, `SettingsScreen`.

Threading: `Dispatchers.IO` for all pollers/bridges; UI observes `StateFlow`s on the
Compose/EDT thread. `OverlayPager` is deliberately touched from both the AWT wheel
listener (same thread as Compose Desktop rendering) and composition — no locks needed
because it's the same thread.

### 5.2 Notch geometry & lifecycle

`OverlayGeometry.kt` owns all footprint math:

- `computeNotchMetrics(config, favoriteCount, musicLive)` → `NotchMetrics` (collapsed/expanded/island/dockArea sizes + edge gap).
- `DockLayout` constants: `IconPadding=4dp`, `ContentPadding=16dp`, `AlongSafety=8dp`, `ExpandedDepthMargin=6dp`, `CollapsedMargin=4dp`, `NowPlayingWidth=356dp`, `LiveCollapsedWidth=236dp`, `DragHandleHeightDp=8`.
- `islandDepth = expandedNotchHeightDp.dp + 6` → **default 84dp**, slider-bounded **70–86**.
- Expanded along-axis length = max(apps row, music page width); apps page is narrower, so the window widens only when the music page is live.
- `overlayScreenLocation(...)` pins the top-centred position, hugging the screen edge (macOS hugs the menu bar; capsule styles float off it by `edgeGapDp`).

Collapse choreography (`FloatingNotchOverlay.kt`):
- `isExpanded` (user-facing expand request) vs `isWindowExpanded` (native window is actually expanded).
- Hover gated by `cursorOverNotch(...)`: real cursor position check, because transparent overlay windows re-fire Enter/Exit while resizing under a stationary cursor. Debounced by `config.collapseDelayMs` (400). Only ever suppresses collapse, never forces expand.
- The **island** keeps growing until `isExpanded || dockState.currentState || dockState.targetState` (dock is a *sibling*, not child — the fading dock is never clipped by the shrinking pill).
- Springs: `notchSizeSpring` (damping 0.9, stiffness 340), `dockRevealSpring`, `pageSpring`, `sharedBoundsSpring` (0.9/320). `DockFadeIn=170ms`, `DockFadeOut=90ms`, `IslandSettleTimeoutMs=650`.
- Open/close waits on the size spring settling (with timeout) before showing the dock / shrinking the window — never resize while content draws.

### 5.3 Widget system & pager

`WidgetPage { Apps, Music }`. The page set (`pages`) is composed from `musicLive`
and `widgets.pinnedAppsEnabled` **with Music first while a track is live** — expanding
a media-showing notch goes into the detailed music view, not the apps dock; the apps
page stays reachable via the wheel.

- Pages are **always composed** (each in a full-size Box with alpha/translationY layer
  transforms by page offset) so the pager never measures the music page mid-animation;
  only the page under the cursor receives input.
- `OverlayPager` owns `pageIndex`/`dockVisible`/accumulator. `pagePosition` is an
  animated float from `pageIndex` (`pageSpring`).
- Wheel paging: **Windows** = AWT `MouseWheelListener` on the overlay window (Compose never
  gets scroll on the unfocused transparent window); **macOS** = Compose `onPointerEvent(Scroll)`
  scaling `preciseWheelRotation` by `stepPx` (30dp.toPx()). One wheel notch = one page.
- On collapse the pager **snaps back to page 0**, so re-opening always lands on the
  default view (Music when live, Apps otherwise).

### 5.4 Shared-element morphs (expand into music)

Whole overlay is wrapped in `SharedTransitionLayout`. The quickview's
`AnimatedVisibility` scope and the dock's `AnimatedVisibility` scope provide the
`AnimatedVisibilityScope` for `sharedBounds` calls. Keys:

```
NowPlayingDiscKey="nowPlayingDisc"  NowPlayingTitleKey="nowPlayingTitle"
NowPlayingVolumeKey="nowPlayingVolume"  NowPlayingBatteryKey="nowPlayingBattery"
```

`SharedTransitionScope.nowPlayingSharedBounds(key, scope)` builds the `sharedBounds`
modifier for every morphing key — the disc, the title and both level rings all use
`scaleInSharedContentToBounds()` / `scaleOutSharedContentToBounds()` (no crossfade —
the default fade enter/exit draws the pill copy and the page copy over each other
mid-flight, so the marquee scrolls twice and the disc/rings flash a duplicate before
settling; one content scaled to the travelling bounds avoids that for all four keys).
The dock reveal is driven by a `MutableTransitionState` (`dockState`) so the visibility
transition doubles as the shared-transition scope.

Quickview visibility while a track is live is `!showDock` (pill stays until the
dock is visible → both surfaces cross in the same frame → morph triggers). Idle/clock
retracts immediately.

### 5.5 Quickview (`QuickView`)

Three branches by state: **live media** (vinyl disc 20dp → art+hub, marquee title 11sp,
volume ring + device-battery ring — each gated by its own collapsed-only toggle),
**idle clock** (`IdleClockRow`, date/time + optional rings), else the **idle dots**.
The drag-handle style shows only a slim grabber line. `LiveProgressStrip` hugs the pill
edge facing the screen centre while playing (collapsed only; auto-hidden behind the
expanded dock). The idle pill widens by one indicator slot (28dp) per visible indicator —
the count comes from `quickViewIndicatorSlots(...)` (device + laptop battery rings, plus
the notification bell when its unread count is readable) and **both** the native
window sizer (`NotchOverlayWindow` in `main.kt`) and the overlay content derive the
collapsed footprint from it, so the rings are never clipped against the window edge.
The notification bell + red count badge (`notification.svg`, `AccentRed`) cap the row
farthest right; the count is polled from the macOS Notification Center store
(`~/Library/Group Containers/group.com.apple.usernoted/db2/db` via `sqlite3`) every 3s —
a store falling Full Disk Access leaves it null and hides the indicator, and Windows has
no unread store yet (see `NotificationCount`).

### 5.6 Media pipeline

- `MediaController` (`Media.kt`, `MediaController.kt`): single `StateFlow<MediaSession?>`
  fed by one platform controller; UI observes it, transport commands go back down.
- Common wire format (JSON, one line per poll/sample) shared by `smtc.ps1` (Windows)
  and the macOS bridge: `{state, src, srcId, title, artist, album, pos, dur, art, id}`.
- `MediaControllerBase.publish()` decodes + **extrapolates pinned progress**: browsers
  register with SMTC but never pump `Position` while playing, so when a PLAYING sample
  repeats the previous raw position for the same content id it becomes a "keep
  advancing from where the last sample extrapolated to" marker. Artwork is cached per
  content id and downscaled to ≤256px (`MediaJson.artworkMaxPx`).
- Windows: `WindowsSmtcController` reads SMTC through `smtc.ps1` (PowerShell 5.1
  projection — this was a hard-won fix). Supports seek.
- macOS: `MacNowPlayingController` runs the compiled `nowplaying-bridge` binary, which
  in turn supervises an `osascript -l JavaScript` subprocess (only *its* context —
  i.e. a `com.apple.*` bundle — can talk to MediaRemote since macOS 15.4). macOS does
  **not** support seek (`supportsSeek=false`, progress display-only).
- The UI keeps a local `nowMs` clock ticking every 250ms while PLAYING so the vinyl and
  progress advance at frame time between 1s polls.

### 5.7 Level rings & batteries

- `LevelRing` (`LevelRing.kt`): one reusable component — 20% open at the bottom, level
  **0–100 over a full 288° arc**, takes a `Painter` (volumePainter / batteryPainter /
  systemBatteryPainter). Used at 22dp in the pill, 28dp in the expanded music page.
- `SystemVolume` (2s): `volume.ps1` over the CoreAudio COM API (Windows) / AppleScript
  `output volume` (macOS). Null hides the chip.
- `AudioBattery` (audio device): presence via WinRT `BluetoothDevice.ConnectionStatus`
  (poll 1s), level via `DEVPKEY_Device_BatteryLevel` (live probe timeout 8s); macOS
  reads `system_profiler SPBluetoothDataType` (≈100 ms — carries both presence + battery,
  polled at 10 s to keep the laptop quiet, `poke()` re-probes instantly on notch-open).
  A compiled watcher (`btwatch_bridge.m`) makes the slow poll event-driven: CoreAudio
  default-output/device-change listeners (plus best-effort bluetoothd distributed /
  Darwin connect notifications) fire the instant a headset routes in or out, and each
  marker triggers an immediate probe (+ one settling re-probe ~1.2 s later), so the ring
  appears within ~1–2 s of the device connecting instead of up to 10 s later.
  The old Bluetooth preferences plist DeviceCache path is dead on modern macOS; kept only
  as a legacy fallback. `poke()` forces
  a probe when the notch opens / music goes live.
- `SystemBattery` (laptop, 5s): `Win32_Battery` via `system-battery.ps1` (Windows) /
  `pmset -g batt` (macOS). Exposes `state` + `hasBattery` (gates the settings toggle so
  desktops never see it). `charging` = **actively charging only** (status 6/7/8/9).
- `SystemAccent` (datasource): OS accent fetch. macOS
  maps `defaults read -g AppleAccentColor` (index incl. `-2` multicolor/`-1` graphite) onto
  system accent RGBs; Windows reads the DWM `AccentColor` (ABGR DWORD) via `accent.ps1`.
  It is published as a `StateFlow` and re-probed every 3s while the System Accent surface
  is chosen (poller gated on `config.surface` inside the overlay), so changing the desktop
  accent recolours the notch in place without a restart; the `IslandSurface` accented style
  rebuilds only when the accent value changes. A failed probe keeps the previous value.
  Null result (unreadable/e.g. PowerShell) → the caller falls back to accent-free
  translucent glass. Feeds `NotchSurface.SYSTEM_ACCENT`; `IslandSurface.buildAccented()`
  blends it into the glass body/rim at ~10–16% so it reads as tinted glass, not a swatch.
- All process spawns: `waitFor(timeout)` then `destroyForcibly()`; failed probes leave
  the previous value (indicator only ever shows real data).

### 5.8 Persistence (`FavoritesRepository`)

`config.json` + `favorites.json` under `%APPDATA%\QuickNotch\` (Windows) /
`~/Library/Application Support/QuickNotch/` (macOS). `NotchConfig` is `@Serializable`
with defaults → old files load fine (missing fields default). Writes are **coalesced**
(single background writer, CONFLATED wake channel): slider drags persist one final
value, config and favorites never interleave. `reorderFavorites(from, to)` is the drag
reorder target. `resetToDefaults()` seeds platform defaults.

### 5.9 Native bridges & resource sync

- `NativeResources.extract(resourcePath, suffix)` unpacks a helper to a temp file for
  execution. Missing resource (e.g. no macOS bridge on a Windows-built jar) = controller
  stays silent — by design.
- Windows PS1 scripts are the single source of truth in `composeApp/native/windows/`;
  `syncWindowsBridge` copies them into resources before jar/processResources.
- macOS bridge: `native/macos/nowplaying_bridge.m` → `cc` → `resources/macos/nowplaying-bridge`
  (macOS hosts only). `dlopen`s MediaRemote, runs JXA via `osascript` for entitlement.
- macOS watcher: `native/macos/btwatch_bridge.m` → `cc` (Foundation + CoreAudio) →
  `resources/macos/btwatch-bridge`. Listens on CoreAudio default-output/device changes
  (+ bluetoothd connect notifications) and prints `AUDIO`/`BT` markers on stdout; the
  audio-battery poller re-probes instantly on each marker. Same build gating as the
  now-playing bridge (`compileMacBtWatchBridge`).

---

## 6. Repo map (where things live)

```
composeApp/
├── build.gradle.kts                      # KMP + Compose Desktop config, bridge sync tasks
├── native/
│   ├── windows/{smtc,volume,system-battery,battery,accent}.ps1   # source of truth for bridges
│   └── macos/nowplaying_bridge.m         # ObjC MediaRemote supervisor
│   └── macos/btwatch_bridge.m            # ObjC CoreAudio route/BT connect watcher
└── src/
    ├── commonMain/composeResources/
    │   ├── values/strings.xml            # ALL user-facing strings
    │   └── drawable/                     # headphone/bluetooth/charging/battery_* SVGs, quicknotch_icon.png
    └── desktopMain/
        ├── resources/                    # tray icon, synced windows/*.ps1, (mac) macos/nowplaying-bridge
        └── kotlin/com/starkdroid/quicknotch/
            ├── main.kt                   # app entry: tray + overlay + settings windows, poller lifecycle
            ├── theme/                    # Color.kt (central palette), Theme.kt, Typography.kt
            ├── model/                    # AppItem, NotchConfig (+WidgetsConfig, enums), FavoritesRepository
            ├── datasource/               # HostPlatform, SystemAccent, scanners/icon loaders/launchers/startup
            ├── media/                    # Media/MediaController(+base), WindowsSmtcController,
            │                             # MacNowPlayingController, AudioBattery, SystemBattery,
            │                             # SystemVolume, NativeResources
            └── ui/
                ├── FloatingNotchOverlay.kt   # THE overlay: island, dock, quickview, morph, music page
                ├── OverlayGeometry.kt        # metrics + screen placement + DockLayout
                ├── OverlayPager.kt           # shared page/flip state
                ├── LevelRing.kt              # reusable 288° ring + painters
                ├── QuickViewClock.kt         # IdleClockRow + NotificationIndicator (+ laptop battery ring)
                ├── IslandSurface.kt          # SOLID/TRANSLUCENT/SYSTEM_ACCENT surface brushes (accent cached per value)
                ├── Shimmer.kt                # label shimmer (Settings/fish-labels)
                └── SettingsScreen / WidgetsTab / DockSizingTab / BehaviorTab / SettingsComponents
                    # SettingsComponents also holds AppearanceDropdown (shared style/surface/date
                    # dropdowns), MinimalistSliderRow, and MinimalistRectSwitch
```

---

## 7. Design system

- Palette in `theme/Color.kt`: `NotchDeepBlack`, `NotchSurface`, `NotchCard`,
  `NotchBorder`, accents (`AccentCyan=#38BDF8`, indigo/purple/pink/green/red),
  `TextPrimary/Secondary/Muted`. Overlay-local accents in `FloatingNotchOverlay.kt`:
  `LiveAccent=#38BDF8`, `VinylDiscFill`, `VinylRing`, `ProgressGradientStart/End`.
- Rounded capsules everywhere, thin borders, dark glass. `LiveAccent` tints live
  media UI. SVG glyphs are tinted via `ColorFilter.tint` (SrcIn) — no hardcoded path data.
- **Level ring**: 20% open at the bottom; full 288°; 0–100; accepts a `Painter`.
- **Vinyl disc** (`VinylDisc`): art clipped to a disc + spindle hub; rotates while
  playing (12s/turn), frozen at its current angle when paused. Used 20dp (pill) and
  56dp (music page) — **both must stay the same visual treatment**.
- **MarqueeText**: single-line title that scrolls when overflowing, speed scaled to
  overflow, 48px gap.
- **Progress bar** (expanded): hairline track + cyan gradient fill + white head ringed
  in the accent riding the fill tip (inset lane so 0%/100% never clip; head only when
  seekable).

---

## 8. Feature inventory & verification status

| Area | State |
| --- | --- |
| Dock app launcher + fisheye wave + labels | done, mature |
| Pinned-apps drag reorder (handle at left, `animateItem`, `onMove` committed on release) | done (`a4e4576`) |
| Now-playing pill + detailed music page + transport | done |
| Expand-into-music + shared-element morph | done (`a02cdc1`, tuned `0c9a9a7`) |
| Volume + audio-battery level rings (both surfaces) | done (`cf9f9be`) |
| Laptop battery ring (collapsed, gated on `hasBattery`) | done (`096b852`) |
| Level-ring glyphs as solid `currentColor` SVG silhouettes (battery/charging/volume), volume ring off Material icons | done |
| Idle clock in pill (date/time formats) | done |
| Settings tabs (Appearance / Widgets / Behavior) | done |
| Settings revamp: section-header icons, notch style + surface dropdowns, `System Accent` surface (OS-accent-tinted glass); tab underglow reverted; descriptive/subtitle texts removed from settings rows and the quickview indicators become square toggle boxes previewing their level ring | done (this session) — visual pass still pending on a live machine |
| Quickview notification indicator: bell + red unread-count badge from the macOS Notification Center store (`NotificationCount`) | done (this session) — hidden until Full Disk Access is granted; visual pass pending |

**Verification matrix (important!):**
- **Windows desktop (the dev machine)**: fully testable — build, run, dock, settings, wheel paging. Media needs a playing track (browsers/SMTC or a music app).
- **Laptop battery UI**: testable on the macOS dev machine (built-in battery → `SystemBattery` talks through `pmset -g batt`). On the Windows desktop it stays null by design.
- **macOS (this machine)**: now a live test surface — build, run, idle clock + laptop battery ring, volume ring (SVG glyphs), audio-device battery ring (via `system_profiler`, AirPods Pro connected), SOFTWARE renderer all verified in-session. Still untestable here: Win32 paths, Windows wheel paging, SMTC seek. The very first pull of this repo may have had no macOS hardware; that is no longer true.
- **Shared-element morph / expand-into-music look**: cannot be judged from code; needs a live session + hover expansion on a real machine. When in doubt, run the app and watch it.

---

## 9. Hard rules — DO / DON'T

**DO**
- Keep all user-facing text in `strings.xml` (`Res.string.x` + `stringResource`).
- Keep all colors in `theme/Color.kt` (or the documented overlay-local accents).
- Keep glyphs as SVGs in `composeResources/drawable/`; tint with SrcIn; never hardcode
  vector path data or icon bytes in code.
- Follow the existing structure/naming/comment style — the codebase is heavily
  documented with `//` narrations explaining *why*. Preserve and extend them.
- Read the surrounding file before editing; treat unfamiliar work as user work.
- Add to the app by building `:composeApp:compileKotlinDesktop` then `:composeApp:desktopJar`
  green, smoke-run with `:composeApp:run`, then commit **and push** on the current branch.
- Reuse `LevelRing`/`VinylDisc`/`MarqueeText`/`TrackProgress` for any music/battery UI.
- Keep shared-element keys/scope rules intact when touching the morph (see 5.4 + pitfalls).
- Use monotonic clocks (`System.nanoTime()`-based) for extrapolation math; sample stamps
  in ms from the same clock.
- Gate pollers behind `HostPlatform.is...` and make failed probes leave last value.

**DON'T**
- Don't force Skiko `SOFTWARE` renderer globally (`build.gradle.kts`): macOS-only at
  runtime; a global override made the Settings window laggy.
- Don't trust PnP presence for Bluetooth audio battery — use
  `BluetoothDevice.ConnectionStatus` for presence, `DEVPKEY_Device_BatteryLevel` for level.
- Don't treat "charging" as anything but actively-charging status (6/7/8/9).
- Don't spawn subprocesses without `waitFor(timeout)` + `destroyForcibly`.
- Don't edit `src/desktopMain/resources/windows/*.ps1` directly — edit `native/windows/` and let `syncWindowsBridge` copy.
- Don't smoke-test with `java -jar` on the plain jar (no main manifest).
- Don't move page state inside the dock `AnimatedVisibility` — re-entry must be stateless (page index/position live outside, snap back to 0 on collapse).
- Don't introduce new user-facing strings inline, new colors outside the palette, or new glyphs outside resources.
- Don't resize/reposition the overlay window from its own live dimensions (feedback loop).
- Don't add a second wheel path on Windows (AWT listener already exists; Compose scroll is macOS-only on purpose, else notches count twice).
- Don't scope-creep: this user drives scope. Implement what is asked, keep the rest consistent with the architecture above.

---

## 10. Pitfalls & gotchas (hard-won)

1. **Compose scroll on Windows overlay never arrives** — attach an AWT
   `MouseWheelListener` in `main.kt`; feed `preciseWheelRotation * stepPx` into `OverlayPager`.
   macOS needs `scrollDelta.x + scrollDelta.y` (Shift+scroll) scaled by the same `stepPx`.
2. **Transparent overlay hover flaps** while content resizes under a stationary cursor —
   always verify with `cursorOverNotch` before collapsing; the gate only suppresses.
   **Never collapse on a single suppressed check**: a fast expand-and-leave detaches the
   hover-footprint node mid-resize, so the first gate reprieve is often stale — a one-shot
   check then wedges the notch open forever. The collapse job must re-poll until the cursor
   is confirmed out (see `setHovered`).
3. **Shared-transition API in CMP 1.7.3**: `sharedBounds`/`sharedElement` require an
   `AnimatedVisibilityScope` (the caller-managed variant isn't public);
   `BoundsTransform` is a plain interface (no SAM) → `object : BoundsTransform {...}`;
   `ResizeMode.ScaleToBounds()` via companion. Verified via `javap` on
   `animation-desktop-1.7.3.jar` in the Gradle cache.
4. **smoke test jar**: plain `composeApp-desktop-1.0.0.jar` has no main manifest — always
   `:composeApp:run`.
5. **Browser SMTC playback freezes progress** — handled in `MediaControllerBase.publish()`
   (pinned-position extrapolation). Don't regress it by treating every poll as authoritative.
6. **macOS entitlements**: since macOS 15.4 MediaRemote only answers `com.apple.*`
   processes — the ObjC bridge must route through `osascript -l JavaScript`. Never "simplify"
   it to a direct MediaRemote call.
7. **Gradients/brushes on the SOFTWARE renderer**: cache brushes per surface at class
   load (see `IslandSurface`, `surface`); the `SYSTEM_ACCENT` style is rebuilt only when
   the OS accent value changes (a rare, user-driven event) and cached per value — no
   per-frame allocation while animating. Never rebuild it per poll tick.
8. **Artwork**: never re-rasterize per poll — cache by content id, downscale to ≤256px.
9. **`AnimatedVisibility` + morph crossing**: the pill must stay visible until `showDock`
   flips while a track is live (`if (musicLive) !showDock else !isExpanded`).
10. **Shared-element morph must not crossfade contents**: the default fade
    enter/exit draws the pill copy and the page copy over each other mid-flight
    (marquee scrolls twice, disc/rings flash a duplicate before settling). Every
    morphing key uses `scaleInSharedContentToBounds`/`scaleOutSharedContentToBounds`
    (one content scaled to the travelling bounds); also keeps the shared-element
    keys/scope shape in section 5.4 intact — never reintroduce a fade on `sharedBounds`.
11. **Settings slider spam**: rely on `FavoritesRepository` coalescing; don't add
    per-tick persistence.
12. **Expanded page vertical budget**: `expandedNotchHeightDp` is 70–86 (+6 margin). The
    music page's right cluster (rings 28 + 4 + transport 34 + 8 floor = 74dp) fits the
    minimum; don't silently exceed it.
13. **Level-ring glyphs must be solid `fill="currentColor"` silhouettes**: stroke-based
    icons (e.g. a `stroke="#000" stroke-opacity="0.32"` battery casing) render at ~1/3
    opacity after the SrcIn tint and read as wrong/faint in the ring. Punch interior
    holes with `fill-rule="evenodd"` (skia SVGDOM honours it); verify any new glyph by
    rasterising the resource through skia, not by eye on one platform.
14. **macOS Bluetooth battery lives in `system_profiler`, not the plist**: the
    `com.apple.Bluetooth.plist` DeviceCache is gone on modern macOS (the file shrinks to
    ~136 bytes of `lastNowPlayingTargetHeadsetAddress`/`lastNowPlayedTime`), so
    `AudioBattery` parses `system_profiler SPBluetoothDataType` instead (device name is
    a heading ending in `:`, attrs are `Key: value` at +8 indent; left/right battery
    averaged, single `Battery Level` next, `Case Battery Level` last; audio = services
    HFP/AVRCP/A2DP/AACP or minor type Headphones/Headset/Earbuds/Audio). BLE-only idle
    links (AirPods in their case, Find My) appear under Connected with a `Services: ...
    < BLE >` line and must NOT publish — only audio-classified devices emit a ring, or
    macOS shows a ghost bluetooth ring with nothing in use. That is not enough on its
    own, though: macOS keeps recent earphones listed as Connected (an audio-capable
    BLE link) even while audio plays through other speakers, so the ring is published
    only when the connected audio device is ALSO the current default output
    (`SPAudioDataType` "Default Output Device: Yes", matched loosely by name). It's ~10× slower
    than the WinRT probe, so macOS polls it at 10 s (not 1 s) and relies on `poke()` for
    instant refresh on notch-open. `ioreg` carries no battery data.
15. **Idle-quickview indicator width must be shared**: the native window sizer
    (`NotchOverlayWindow` in `main.kt`) and the overlay content each build their own
    `NotchMetrics`. If the indicator-slot count diverges they disagree on the collapsed
    footprint and the right-most indicator clips off the window edge — locally: laptop
    battery ring "missing". Always derive the count via `quickViewIndicatorSlots(...)`
    (and reset `AudioBattery._info` on every macOS empty-probe, so a disconnected ring
    never lingers). The notification bell adds one slot, so a readable/unreadable store
    flips the collapsed width by 28dp — both sizers must feed it the same `NotificationCount`
    value.
16. **Real-time BT connection events must come from CoreAudio, not bluetoothd
    notifications**: bluetoothd's `com.apple.bluetooth.device-connected` /
    `-disconnected` names fire via neither NSDistributedNotificationCenter nor
    `notify_register_dispatch`/`notifyutil -w` on current macOS (verified with a power
    toggle and a live connect attempt; nothing arrives). The reliable real-time signal
    is a CoreAudio property listener on `kAudioHardwarePropertyDefaultOutputDevice`
    (+ `DefaultSystemOutputDevice` + `Devices`) — it fires exactly when a headset
    routes in/out, which is what `btwatch_bridge.m` uses to poke the poller. Lowering
    the `system_profiler` cadence instead would cost ~5–7% of a core (≈150 ms/probe), so
    keep the 10 s poll as the backstop and the watcher as the trigger.

---

## 11. Git workflow

- Branch: `cursor/macos-compose-adaptation`; work here, commit, `git push`.
- One logical change per commit; message = imperative summary matching recent history
  (e.g. "Reorder pinned apps by dragging instead of arrow buttons").
- Before committing: `compileKotlinDesktop` + `desktopJar` green + smoke run.
- Keep `git status` clean at the end of a task; note untestable paths honestly in the summary.

---

## 12. Scope guardrails

- Current scope (recent + stable): level rings, audio/laptop battery, drag-reorder,
  expand-into-music with shared-element morph, unified pill/expanded visuals,
  transport/progress polish. Keep these intact and styled consistently.
- Out of scope unless asked: new notch positions (explicitly removed), window blur
  (unreachable without screen capture — `NotchSurface.TRANSLUCENT` is texture-free glass),
  Android/iOS targets, marketing site (`web/`) changes.

---

## 13. Environment notes (dev machine)

- **Windows desktop, no battery** — laptop-battery UI untestable locally.
- **macOS MacBook (current session host)** — macOS build/run verified in-session; built-in
  battery exercises the laptop-battery ring. Windows-only paths still unverified.
- Test Bluetooth audio device: **realme Buds Air 5**, MAC `84:0F:2A:94:24:0A`.
- Temp/scratch dir for external tools: `C:\Users\gamer\AppData\Local\Temp\opencode`.
- Long-running GUI smoke runs: start with a timeout and clean up leftover
  `java` processes whose command line matches the app.

---

## 14. Recent history digest (why the code is shaped this way)

- `c61040f`–`0b85fd5`: media controller + native bridges + music live activity + widgets tab.
- `01036eb`: collapsed island became a live music strip; later versions dropped the dead
  pill play/pause in favor of live volume.
- `b49cba6`/`3f9c843`: pill optical centering, wheel paging (Windows), idle clock.
- `f95aed0`/`8839301`: frozen browser progress fix; near-instant BT connect/disconnect.
- `4f0c450`–`cf9f9be`: reusable level ring; audio-device battery; resource/localization pass.
- `096b852`: laptop battery ring (gated on `hasBattery`).
- `a4e4576`: drag-to-reorder pinned apps replacing arrows.
- `a02cdc1` → `0c9a9a7`: expand-into-music + shared-element morph; unified vinyl disc,
  rings stacked above transport, scale-based title morph, gradient progress redesign.
- `0c9a9a7` → (pending): ring glyphs became solid `fill="currentColor"` silhouettes
  (battery/charging/volume) so every level-ring icon tints fully on macOS — the old
  32%-opacity stroke battery casing was nearly invisible once tinted. Volume ring now
  uses the packaged `volume.svg` instead of Material icons.

---

## 15. Quick constants reference

| Constant | Value | Where |
| --- | --- | --- |
| `quickViewWidthDp` / `quickViewHeightDp` | 150 / 30 (default; serialised as `collapsedPillWidthDp`/`collapsedPillHeightDp`) | `NotchConfig` |
| `expandedNotchHeightDp` | 78 (slider 70–86) | `NotchConfig`, `DockSizingTab` |
| island depth | height + 6dp | `OverlayGeometry` |
| `NowPlayingWidth` / `LiveCollapsedWidth` | 356dp / 236dp | `DockLayout` |
| `FisheyeRadiusFactor` / `IconLift` | 1.5× / 8dp | overlay |
| `HoverGlow` / `IslandSettleTimeoutMs` | 10dp / 650ms | overlay |
| springs | notch 0.9/340, sharedBounds 0.9/320, icon 0.8/1000 | overlay |
| `ArtworkMaxPx` | 256 | `MediaController` |
| Poll cadence | volume 2s, audio-battery 1s presence (+8s live probe), system-battery 5s | media pollers |
| nowMs tick | 250ms while PLAYING | overlay |
| Page step | 30 dp of wheel travel | `OverlayPager` |

---

*Keep this doc honest: when a fact changes, update it in the same commit. When you
discover a new gotcha, add it under section 10.*