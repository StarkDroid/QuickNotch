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

On macOS the pill lives **inside the menu-bar band, flush against the true top
of the screen** (above the menu bar itself — a real Dynamic Island placement),
joins every Space and survives full-screen apps while Always-on-top is on. That
position is unreachable through AWT, so an ObjC bridge parks/keeps the window
there (see 5.10/5.11). The app is a **quiet agent when only the notch is open**
(no Dock icon, no app menu bar, overlay layers over full-screen apps); the Dock
icon, app switcher entry and activation appear **only while the Settings window
is open**. The Windows overlay is likewise excluded from the taskbar/Alt+Tab
(`WS_EX_TOOLWINDOW | WS_EX_NOACTIVATE`) — only Settings is a taskbar window.

One Kotlin/Compose Multiplatform (Desktop JVM) codebase runs both platforms;
only scanning, icons, launch, login-items, media/battery bridges, window levels
and Dock/taskbar policy differ (Win32/JNA + PowerShell vs. macOS
`open`/`sips`/`plutil`/`pmset`/Objective-C).

The repo also contains `web/` (a separate SvelteKit marketing site) — unrelated
to the app build, ignore it unless explicitly asked.

---

## 2. Repo fast facts

- Branch: `cursor/macos-compose-adaptation` (all recent widget work lands here).
- App-code HEAD at doc creation: `0c9a9a7` — "Unify vinyl disc, stack rings over transport, morph title by scale, restyle progress". (This doc and its commits sit on top.)
- No test source set exists. Verification = **compile + desktop jar + `:composeApp:detekt` + manual/visual smoke run**.
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
| HTTP / dates | Ktor 3.1.3 client (CIO) + kotlinx-datetime 0.6.2 — see pitfall 35 |
| Native | JNA 5.16.0 + jna-platform (Win32), PowerShell 5.1 bridges, macOS ObjC MediaRemote bridge |
| Renderer | **Skiko 0.8.25 pinned** (see pitfall 28) — METAL on macOS, DIRECT3D on Windows. `software-render` marker file in the app-data dir forces the CPU rasterizer |
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

**Rendering backend (macOS).** The app renders with Skiko's METAL backend; the
startup line `QuickNotch: skiko render backend = METAL` confirms it. This is the
single biggest performance lever in the app (~3.4ms of CPU per settings scroll
frame vs ~70ms on the CPU rasterizer), so before suspecting any composable of
being slow, check that line says `METAL` — see pitfall 28. To A/B test
backends without editing code:

```bash
SKIKO_RENDER_API=SOFTWARE .\gradlew.bat :composeApp:run    # force the rasterizer once
```

and as a durable escape hatch, drop an empty `software-render` file in the
app-data dir (`~/Library/Application Support/QuickNotch/`), which forces
SOFTWARE on every launch.

**Smoke-test with `:composeApp:run`, never `java -jar`**: the plain desktop jar
carries no main manifest. To stop a `run` session, kill the `java` process whose
command line matches `MainKt|quicknotch`.

`desktopJar`/`desktopProcessResources` depend on two custom tasks (see
`composeApp/build.gradle.kts`):

- `syncWindowsBridge` — copies `composeApp/native/windows/*.ps1` → `src/desktopMain/resources/windows/`.
- `compileMacNowPlayingBridge` — builds `native/macos/nowplaying_bridge.m` with `cc` (macOS hosts only).
- `compileMacBtWatchBridge` — builds `native/macos/btwatch_bridge.m` with Foundation + CoreAudio (macOS hosts only).
- `compileMacPassthroughBridge` — builds `native/macos/passthrough_bridge.m` into a dylib with Foundation + AppKit (macOS hosts only; loaded by `MacAppKit` and used by `WindowPassthrough`, the overlay window policy and the Dock-presence toggle).

If a Windows `.ps1` script is edited in `native/windows`, the copy into
`resources/windows` is refreshed automatically at jar time — but **during a plain
`run` the app reads the copied resource**, so keep the two in sync.

---

## 5. Architecture

### 5.1 Process & windows (`main.kt`)

- One `application {}`; `FavoritesRepository` + `MediaController` are singletons owned here.
- **Overlay window**: undecorated, transparent, `alwaysOnTop`, non-resizable, top-centred. The native window holds a **single fixed footprint — the expanded dock size — for its whole life** and is never resized while the notch opens or collapses; expanding is purely drawn growth of the island inside that stable window. This is what kills the macOS resize flash: a transparent AWT window that resizes white-flashes/skips a frame (known Compose Desktop + Skiko issue, see pitfall 17). The fixed window's transparent margin previously swallowed clicks on the desktop around the collapsed pill; `WindowPassthrough` (see 5.10) now clips the hit-test to exactly the drawn island, so the margin is click-through unless the cursor actually sits over the island (see pitfall 17). It is still **atomically positioned** (size+position written together in one `LaunchedEffect`) after the AWT window maps, and re-centred whenever the target footprint changes (favourites count, the widened now-playing page). `LaunchedEffect(config.style, targetSize)` handles placement; never re-place on the window's own live dimensions (feedback loop).
  - **macOS above-menu-bar placement** (`MacOverlayWindowPolicy` via `MacAppKit`): AWT's `alwaysOnTop` only reaches `NSFloatingWindowLevel` (3) — below `kCGMainMenuWindowLevel` (24) — and AWT clamps placement to the visible area (y ≥ menu-bar height), so the island would always park *under* the menu bar ("below the screen", see pitfall 18). On macOS the overlay policy instead parks the window with direct Cocoa calls: level `NSStatusWindowLevel` (25, above the menu bar), collection behaviour `canJoinAllSpaces | stationary | fullScreenAuxiliary | ignoresCycle` (joins every Space, survives full-screen apps), and a frame top-gap of `edgeGapDp` so the island sits **flush against the true top of the screen** (verified: CGWindowList layer 25, bounds y=0). With **Always-on-top off** the policy drops the window to `NSNormalWindowLevel` (0) and re-parks it below the menu bar (`menuBarHeight + edgeGap`), so normal windows can cover the notch again. A slow reconcile loop (~700 ms) re-asserts level + frame because Compose placement and AWT's z-order bookkeeping keep re-parking the window 30pt low; installed only on macOS, off-mac it's a no-op.
- **Dock / taskbar presence** (`MacDockPresence` via `MacAppKit`): the app is a **quiet agent** while only the notch runs — macOS `setActivationPolicy(NSApplicationActivationPolicyAccessory)` ⇒ no Dock icon, no app menu bar, and the overlay can sit over full-screen apps. Opening the Settings window flips it to `Regular` and brings it to the front **once** (Dock icon, switcher entry, focus); closing Settings returns it to accessory. A ~700 ms reconcile loop re-asserts **only the policy** (AWT initialises the process as Regular and can fight a one-shot flip) — it deliberately never re-activates, because per-tick `activateIgnoringOtherApps` would steal focus back from every other app whenever Settings is open (see pitfall 19). The Settings window is an ordinary non-topmost window. On Windows the overlay window itself is created with `WS_EX_TOOLWINDOW | WS_EX_NOACTIVATE` (no taskbar slot, no focus steal) while the Settings window stays a normal taskbar window — the same "presence only with Settings" rule. Both are driven from `showSettings` in `main.kt`; verified live: `lsappinfo` reports `type="UIElement"` notch-only and `type="Foreground"` with Settings open, and a second app (TextEdit) stays frontmost across many reconcile ticks once Settings is open.
- **Tray** (Windows) / menu-bar extra (macOS): show/hide notch, an **Always on top** toggle (mirrors the Behavior-tab switch; drives `config.alwaysOnTop`, re-applied to the overlay at next open), open Settings, reset defaults, quit.
- **Settings window**: 880×660, normal window, `SettingsScreen`. A **fresh manual launch** (Spotlight/Dock/Finder/Launchpad) opens it alongside the notch so the app's presence is obvious and configuration is one step away; a **login/boot auto-start** stays quietly behind the tray instead. The distinction is a command-line marker (`StartupManager.LAUNCHED_AT_LOGIN_ARG`) that `main` checks: it's appended to the macOS LaunchAgent / Windows Run-key command when auto-start is enabled, so `main(args)` starts with `showSettings = !args.contains(marker)`. Jpackage forwards `open -a App --args <marker>` arguments to the JVM main (verified via CGWindowList on both paths); an existing login item written before this feature has no marker and will open Settings at boot once until toggled off/on to regenerate it.

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
- `isExpanded` (user-facing expand request) is purely drawn growth inside the fixed native window — no `isWindowExpanded`/window-resize step anymore.
- Hover gated by `cursorOverNotch(...)`: real cursor position check, because transparent overlay windows re-fire Enter/Exit while content resizes under a stationary cursor. Debounced by `config.collapseDelayMs` (400). Only ever suppresses collapse, never forces expand.
- The **island** keeps growing until `isExpanded || dockState.currentState || dockState.targetState` (dock is a *sibling*, not child — the fading dock is never clipped by the shrinking pill).
- Springs: `notchSizeSpring` (damping 0.9, stiffness 340), `dockRevealSpring`, `pageSpring`, `sharedBoundsSpring` (0.9/320). `DockFadeIn=170ms`, `DockFadeOut=90ms`, `IslandSettleTimeoutMs=650`.
- Open waits on the size spring settling (with timeout) before showing the dock; close lets the island spring back first. The window itself never resizes (pitfall 17).

### 5.3 Widget system & pager

`WidgetPage { Apps, Music }`. The page set (`pages`) is composed from `musicLive`
and `widgets.pinnedAppsEnabled` **with Music first while a track is live** — expanding
a media-showing notch goes into the detailed music view, not the apps dock; the apps
page stays reachable via the wheel.

- Pages are **always composed** (each in a full-size Box with alpha/translationY layer
  transforms by page offset) so the pager never measures the music page mid-animation;
  only the page under the cursor receives input.
- "Only the active page receives input" must be enforced by **not composing the pointer
  modifiers at all**, not by disabling them: `clickable(enabled = false)` /
  `pointerInput(enabled = false)` still register a hit-testable node, so an inactive
  (alpha 0) page would keep occupying the hit path of the page stacked beneath it. Every
  control inside `WidgetPages` therefore gates its modifier behind `interactive` — the
  dock icons (`DockIcon`), the transport buttons + play circle, the mute ring
  (`.then(if (interactive) Modifier.clickable … else Modifier)`) and the vinyl disc
  (`onClick = null`, which is how `VinylDisc` drops its own clickable).
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

`SharedTransitionScope.nowPlayingSharedElement(key, scope)` builds the `sharedElement`
modifier for every morphing key — the disc, the title and both level rings. It must stay
`sharedElement` and never regress to `sharedBounds`: sharedBounds *by design* keeps both
the entering and the exiting content visible inside the travelling frame (its enter/exit
only style them — bytecode-verified: `scaleInSharedContentToBounds()` is literally
`EnterTransition.None + ContentScaleTransitionEffect`), so the pill copy and the page
copy double-draw mid-flight — two covers, two titles (the marquees also scroll out of
register), rings printed twice. `sharedElement` renders only the target content in the
transforming bounds, so there is always exactly one cover and one title; it is the
correct primitive here because both sides render the *same content* at different sizes
(disc 20↔56dp, title 11↔14sp, rings 22↔28dp). The dock reveal is driven by a
`MutableTransitionState` (`dockState`) so the visibility transition doubles as the
shared-transition scope.

Quickview visibility is `!showDock` on both branches: while a track is live the pill
stays until the dock is visible → both surfaces cross in the same frame → morph triggers.
The idle pill uses the same cross but as an "curtain": it stays centred as the island
grows (drifting down with the box's centering), then slides down past the revealed dock
(`QuickViewSlideIn/Out`, 160/190ms) while the pinned apps scale up from behind it
(`IdleDockEntryScale=0.82`, anchored `IdleDockOrigin≈top`). Live music deliberately skips
both — its plain fade exits while the music page pops in with the old gentle 0.97 scale.

### 5.5 Quickview (`QuickView`)

Three branches by state: **live media** (vinyl disc 20dp → art+hub, marquee title 11sp,
volume ring + device-battery ring — each gated by its own collapsed-only toggle
(the volume ring mirrors the expanded page's mute treatment: level 0 + dimmed
glyph/ring), and
optionally the `MusicWaveform` bar lane stacked *under* the title inside the same 22dp
title slot, which `widgets.waveformHidesTitle` can turn into a title-less layout where
the analyser owns the whole 18dp frame and the pill reads as disc + bars + rings — and
which settles onto a flat line whenever the track pauses),
**idle clock** (`IdleClockRow`, date/time + optional rings), else the **idle dots**.
The drag-handle style shows only a slim grabber line. `LiveProgressStrip` hugs the pill
edge facing the screen centre while playing (collapsed only; auto-hidden behind the
expanded dock, and hidden outright while `widgets.showCollapsedWaveform` is on — the bars
take that band, so the two never compete for the same pixels). The idle pill widens by one indicator slot (28dp) per visible indicator —
the count comes from `quickViewIndicatorSlots(...)` (device + laptop battery rings, plus
the notification bell when its unread count is readable and the toggle is on) and **both** the native
window sizer (`NotchOverlayWindow` in `main.kt`) and the overlay content derive the
collapsed footprint from it, so the rings are never clipped against the window edge. The
waveform deliberately lives inside the title's existing 22dp slot rather than claiming an
indicator slot, so the collapsed footprint (and every morph key) is untouched by it.
The notification bell + red count badge (`notification.svg`, `AccentRed`) cap the row
farthest right, gated by the `showCollapsedNotifications` widget toggle; the value is read
from the macOS Notification Center store
(`~/Library/Group Containers/group.com.apple.usernoted/db2/db` via `sqlite3`) and updated
**near-instantly**: the poller stat fingerprints `db`, `db-wal` and `db-shm` on a 250 ms
tick (a few syscalls, no sqlite spawn) and only runs the query when a signature changes —
the store is in WAL mode, so an arriving/dismissed notification churns `db-wal`, not the
main `db`, hence all three files are watched. A 3s backstop query still runs so a missed
signal converges.

The value shown is the **number of notifications currently in the Notification
Center** — the store's `record` table is the ledger that *backs* the center: one
row per delivered-not-yet-acknowledged notification, so `count(*)` is live in both
directions (a delivery inserts, a dismiss/Open/Clear deletes; a Clear All in-session
dropped 59 → 15). One honesty caveat: macOS keeps the notifications of apps that
still hold a Dock-badge unread count (WhatsApp, Messages) after a Clear All until
those apps are read/dismissed, so the count legitimately sits above zero after
clearing — the same rows the OS itself is holding, and the same reason the Dock
badges survive. (The `app.badge` sum was evaluated and rejected: badges don't move
on dismissal, so it couldn't reflect removals.) The store
is guarded by Full Disk Access, so until a grant the query fails and the count stays null —
the indicator hides like any other unreadable probe, and settings offers a **Grant Full
Disk Access** button that deep-links the Privacy &amp; Security pane
(`x-apple.systempreferences:com.apple.preference.security?Privacy_AllFiles`); the settings
window re-pokes the store on focus regain (`NotificationCount.poke()`), so a grant made in
System Settings is detected as soon as the user comes back. Once the store reads there is
nothing left to configure, so the helper row disappears. (An earlier **Send Test
Notifications** flow — `osascript` banners on macOS, a session-local tally + WinRT toast on
Windows — shared that row slot; it was removed by request: the badge is a real NC ledger,
not a demo surface, so Windows now stays null and its bell hides rather than show an
always-zero dead icon. The `toast.ps1` bridge was deleted with it.) Windows has no
public unread store — no live per-app counts there.
Settings' QuickView Indicators card mirrors the toggle (a bell cell instead of a ring —
a notification has no level to preview, and deliberately **no numeric badge**: a hardcoded
count would misrepresent the live Notification Center number, which only ever renders on
the island itself) and, while the toggle is on, shows the helper row described above.

### 5.6 Media pipeline

- `MediaController` (`Media.kt`, `MediaController.kt`): single `StateFlow<MediaSession?>`
  fed by one platform controller; UI observes it, transport commands go back down.
- Common wire format (JSON, one line per poll/sample) shared by `smtc.ps1` (Windows)
  and the macOS bridge: `{state, src, srcId, title, artist, album, pos, dur, art, id}`.
- `MediaControllerBase.publish()` decodes + **extrapolates pinned progress**: browsers
  register with SMTC but never pump `Position` while playing, so when a PLAYING sample
  repeats the previous raw position for the same content id it becomes a "keep
  advancing from where the last sample extrapolated to" marker. Artwork is cached per content id and decoded **straight to** ≤256px
(`MediaJson.artworkMaxPx`) via ImageIO `sourceSubsampling`, so a 1200px JPEG never
materialises at full size (the old decode-then-copy-then-scale path cost 19–49ms of
pure copying per track, 131ms cold); the same decode pass also derives `MediaSession.artTint` — the cover's dominant colour
(`dominantArtTint`: a coarse population×chroma histogram over a sampled grid) computed
**once per track on the poller's worker thread** and cached with the bitmap, so the
waveform can take the album's colour without sampling pixels in composition.
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
  `output volume` (macOS). Each poll spawns ONE process that answers
  `"<percent>,<muted>"` (the PS script takes `-Action get|mute|unmute` for writes);
  both values are exposed as flows and null hides the chip. `toggleMute()` flips the
  OS mute (fire-and-forget on IO) + pokes an immediate re-read; the **expanded music
  page's volume ring doubles as the mute toggle** (click → mute/unmute, ring empties
  to level 0 with a dimmed glyph while muted; contentDescription from
  `music_mute`/`music_unmute`). The collapsed pill's ring stays non-interactive and
  keeps showing the raw percent.
- `AudioBattery` (audio device): presence via WinRT `BluetoothDevice.ConnectionStatus`
  (poll 1s), level via `DEVPKEY_Device_BatteryLevel` (live probe timeout 8s); macOS
  reads `system_profiler SPBluetoothDataType` (≈100 ms — carries both presence + battery,
  polled at 10 s to keep the laptop quiet, `poke()` re-probes instantly on notch-open).
  The device ring draws the device mark (`headphone`/`bluetooth`, one glyph per
kind) over the charge sweep, and badges the connected-device count when more
than one is around; the machine's ring draws the fill silhouette
(`battery_*`/`charging`). A compiled watcher
(`btwatch_bridge.m`) makes the slow poll event-driven: CoreAudio
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

### 5.10 Window passthrough (`WindowPassthrough.kt`)

Because the overlay native window keeps one fixed (expanded) footprint for its whole
life, its transparent margin would swallow clicks on whatever app or desktop sits
around the collapsed pill. `WindowPassthrough` (datasource, `main.kt`) clips the
window's interactive area to exactly the drawn island each tick:

- The overlay publishes the **drawn island's** bounds (a `java.awt.Rectangle` in
  window-client *physical* pixels, straight from `localToWindow(Offset.Zero)` — Compose
  px are already physical, so no further scaling) via `onTouchTargetChange`; `null` =
  "whole window interactive" (measured/first frame). Deliberately NOT the wider hover
  box (island + `HoverGlow`): the glow stays a hover-only comfort margin, while the
  native touch target hugs the pill so the click-through starts right at its edge.
  An earlier version multiplied the rect by `density` again — double-scaling the
  interactive area on every display above 100% into an invisible click-swallowing band
  (invisible on the scale-1.0 dev Mac, which is why it hid so long).
  **Publishing is inline in the island Box's `onGloballyPositioned`** — never key an
  effect on the coordinates state: the `LayoutCoordinates` instance is stable across
  layout passes, so a keyed effect ran exactly once and froze the touch target at the
  collapsed pill forever — once the notch expanded, every control outside the stale
  rect (transport buttons!) passed clicks through to the apps below. The callback
  itself fires on every position/size change, which is exactly when the target must be
  republished. The synthetic MOUSE_MOVED re-arm is also delayed ~40ms so it cannot race
  the async AppKit flip (an immediate fire lost the enter intermittently).
- **Windows**: `SetWindowRgn(GDI32.CreateRectRgn(...))` — the region clips both painting
  and hit-testing; the OS frees the previous region when replaced, so a fresh region per
  tick is safe (inflated by 2px so a click grazing the pill's edge lands on the island).
  `null` region = full window. On construction the overlay also gets
  `WS_EX_TOOLWINDOW | WS_EX_NOACTIVATE` (via `User32.GetWindowLong/SetWindowLong`,
  `GWL_EXSTYLE`), so it never occupies a taskbar slot or steals focus.
- **macOS**: `[NSWindow setIgnoresMouseEvents:]` (whole-window, no per-region variant),
  driven cursor-first each 30 ms poll: the window is mouse-transparent unless the *real*
  cursor (via `MouseInfo`) sits within the island rect (brought back to logical
  points via the graphics configuration's scale — a no-op on non-scaled displays —
  plus a 4px pad). The opposite of
  the Compose gate: a suppressed hover plays no role here — this is a raw
  AppKit-level hit-test, and poll cadence is what reactivates clicks. When the window is
  flipped back to interactive, a synthetic `MOUSE_MOVED` is dispatched so Compose's hover
  machinery sees the "enter" it never received while mouse-transparent. The island's
  screen position comes from the **real** CG origin (`quicknotch_get_window_cg_origin`),
  not AWT's cached `locationOnScreen` (which reports y=0 at the bottom of the menu bar
  and never learns about the above-menu-bar Cocoa moves — see pitfall 18). The AppKit
  toggle itself runs through the compiled `native/macos/passthrough_bridge.m` dylib
  (loaded through the shared `MacAppKit` JNA front; `quicknotch_set_ignores_mouse_events`,
  dispatched on AppKit's main queue) — plain-C args are reliable, where driving
  objc_msgSend from JVM land with a void return / BOOL arg crashes the JVM (SIGTRAP on
  Apple silicon).
- Both platforms resolve the native handle by reflecting the AWT peer: the private
  `Component.peer` field → `LWWindowPeer.getPlatformWindow` → the protected
  `CFRetainedResource.ptr` (the NSWindow; no `getNSWindowPtr` exists on JDK 17) /
  `WComponentPeer.getHWnd`. The packages are opened via
  `--add-opens java.desktop/java.awt=ALL-UNNAMED`,
  `--add-opens java.desktop/sun.lwawt.macosx=ALL-UNNAMED` and
  `--add-opens java.desktop/sun.awt.windows=ALL-UNNAMED` (added to
  `compose.desktop.application` so they apply to `run` and the packaged app).
- **Fail-soft by design**: `install()` never throws; if the native handle can't be
  resolved, the bridge is missing, or the pointer fails the `respondsToSelector:` probe,
  the window simply stays fully interactive — the old fixed-footprint margin behaviour,
  degraded but never broken. A one-shot `QuickNotch: macOS overlay passthrough
  armed/off (...)` line is logged at startup.
- **Threading (critical — see pitfall 21)**: the 30ms tick runs on `Dispatchers.IO`.
  macOS `apply()` runs **off the AWT EDT** (the dylib marshals onto AppKit's main
  queue itself; running the tick on the EDT first cost a 33Hz EDT→main-queue
  `dispatch_sync` and deadlocked against AWT's own hops — the "not responding"
  beachball). Windows still applies its region on the EDT (owner-thread courtesy);
  macOS's synthetic MOUSE_MOVED re-arm is the only EDT hop, via `invokeLater`.

### 5.11 Platform integration study (Windows/macOS KMP desktop)

A deliberate survey of how far each platform's native APIs can be reached from the
single Kotlin/Compose Desktop codebase, feeding §5.10 and the above-menu-bar +
Dock-presence work. Findings in brief:

- **AWT/Compose limits on macOS** (verified against JDK 17 source and live):
  `setAlwaysOnTop` tops out at `NSFloatingWindowLevel` (3) — *below* the menu bar
  (24) — and placement clamps to the visible frame (y ≥ menu-bar height). A
  Dynamic Island therefore cannot live above the menu bar via AWT. `locationOnScreen`
  returns a *cached* origin that never observes direct Cocoa frame moves, so any
  AWT-side screen math (hover gating, passthrough) drifts exactly by the menu-bar
  height once the window is parked above it.
- **The right layer for an island is AppKit's `NSStatusWindowLevel` (+1 over the
  menu bar) with `collectionBehavior = canJoinAllSpaces | stationary |
  fullScreenAuxiliary | ignoresCycle`** — the same recipe the popular notch
  utilities use; it yields a window that joins every Space and survives in front of
  full-screen apps. Mirrored on Windows by `IsWindowTopmost`-style `alwaysOnTop`
  plus the region-clipped tool-window.
- **Dock/taskbar presence is `setActivationPolicy` on macOS** — accessory (no Dock
  icon, silent agent) ↔ regular (Dock icon, switcher) toggled live — and
  `WS_EX_TOOLWINDOW | WS_EX_NOACTIVATE` window styles on Windows. Both give the
  same rule: *app visible in the OS chrome only while Settings is open*.
- **Calling AppKit from the JVM**: compiled C symbols in a dylib (JNA) beat raw
  `objc_msgSend` every time — void/BOOL sends SIGTRAP the JVM on Apple silicon.
  All AppKit work is marshalled onto the main queue inside the dylib.
- **Windows deep integration** already uses JNA-platform's full Win32 surface:
  `User32` (window styles/regions/wheel), `GDI32` (`CreateRectRgn`), DWM accent
  via `accent.ps1`, `Shell32` `SHGetImageList` for jumbo taskbar-style icons, and
  PowerShell 5.1 projections for SMTC, CoreAudio volume, battery WMI and WinRT
  Bluetooth presence (see §5.7). PowerShell was chosen over Java libs because
  SMTC/`BluetoothDevice` have no stable pure-Java projection.
- **macOS media/battery/mining** stays in small compiled ObjC binaries +
  AppleScript/JXA + CLI tools (`osascript`, `pmset`, `system_profiler`,
  `sqlite3`) because MediaRemote since 15.4 only answers `com.apple.*`
  processes (the JXA subprocess trick, §5.6) and SPI plumbing would be unscoped.
- **Threading contract**: everything native runs behind `Dispatchers.IO` objects;
  their AppKit hops are dispatched to the main queue so the UI thread never blocks.
  Fail-soft loading (bridge absent → features stay silent) is the project-wide
  rule, so the Windows jar built on a macOS host still runs everything else.

---

## 6. Repo map (where things live)

```
composeApp/
├── build.gradle.kts                      # KMP + Compose Desktop config, bridge sync tasks
├── native/
│   ├── windows/{smtc,volume,system-battery,battery,accent}.ps1   # source of truth for bridges
│   └── macos/nowplaying_bridge.m         # ObjC MediaRemote supervisor
│   └── macos/btwatch_bridge.m            # ObjC CoreAudio route/BT connect watcher
│   └── macos/passthrough_bridge.m        # ObjC main-queue AppKit dylib: ignoresMouseEvents,
│                                         # window level/collection/frame-top-gap, CG origin,
│                                         # activation policy (MacAppKit front)
└── src/
    ├── commonMain/
    │   ├── kotlin/com/starkdroid/quicknotch/calendar/
    │   │   ├── CalendarApi.kt            # every Google request, on Ktor CIO (commonMain, no java.*)
    │   │   └── CalendarEvent.kt          # event model + ticker window (kotlinx-datetime)
    │   ├── composeResources/
    │   ├── values/strings.xml            # ALL user-facing strings
    │   └── drawable/                     # battery_{full,mid,low}/charging/volume/notification SVGs,
    │                                     #   quicknotch_icon.png
    └── desktopMain/
        ├── resources/                    # tray icon, synced windows/*.ps1, (mac) macos/nowplaying-bridge,
        │                                #   btwatch-bridge, passthrough-bridge.dylib
        └── kotlin/com/starkdroid/quicknotch/
            ├── main.kt                   # app entry: tray + overlay + settings windows, poller lifecycle
            ├── theme/                    # Color.kt (central palette), Theme.kt, Typography.kt
            ├── model/                    # AppItem, NotchConfig (+WidgetsConfig, enums), FavoritesRepository
            ├── datasource/               # HostPlatform, AppPaths (app-data dir), SystemAccent,
            │                             #   WindowPassthrough, MacAppKit
            │                             # (appkit/level/dock-policy front), scanners/icon loaders/
            │                             # launchers/startup
            ├── calendar/                 # GoogleCalendar: OAuth (PKCE + loopback) -> today's
            │                             #   events, tokens in calendar.json, ticker/Connected flows
            │                             #   (desktop only: loopback socket, file store, browser hand-off)
            ├── media/                    # Media/MediaController(+base), WindowsSmtcController,
            │                             # MacNowPlayingController, AudioBattery, SystemBattery,
            │                             # SystemVolume, NativeResources
            └── ui/
                │   # The overlay is modular: root choreography in FloatingNotchOverlay.kt,
                │   # pieces split into focused sibling files (see below). New overlay
                │   # components belong in the file that owns their concern, not the root.
                ├── FloatingNotchOverlay.kt   # overlay ROOT: state + choreography only (hover gate,
                │                             # dock reveal, springs wiring, shared-transition scopes)
                ├── QuickView.kt              # collapsed pill branches (live media / clock / dots) + LiveProgressStrip
                ├── DockContent.kt            # widget pages + music page + fisheye dock (Dock/DockIcon/DockLabel)
                ├── MediaWidgets.kt           # VinylDisc, MarqueeText, TrackProgress, transport, isMusicLive, tints
                ├── NowPlayingMorph.kt        # shared-element keys + nowPlayingSharedElement (sharedElement!)
                ├── OverlayHover.kt           # SharingHoverNode/Element + cursorOverNotch (collapse gate)
                ├── NotchMotion.kt            # springs, timing constants, fisheyeScale, PillAnimationFps
                ├── OverlayGeometry.kt        # metrics + screen placement + DockLayout + notchShape
                ├── OverlayPager.kt           # shared page/flip state
                ├── LevelRing.kt              # reusable 288° ring + painters
                ├── QuickViewClock.kt         # IdleClockRow + NotificationIndicator (+ laptop battery ring)
                ├── IslandSurface.kt          # SOLID/TRANSLUCENT/SYSTEM_ACCENT surface brushes (accent cached per value)
                ├── Shimmer.kt                # icon-skeleton shimmer (draw-phase-only invalidation)
                ├── CalendarWidget.kt         # Widgets-tab Google Calendar (connect/refresh/status) +
                │                             #   CalendarDayIndicator for the collapsed pill's mail badge
                ├── OnboardingScreen.kt       # first-run flow: welcome -> style -> calendar -> apps, live notch
                │                             #   preview carried between steps in a stage card + segmented progress;
                │                             #   config.onboardingDone gates the overlay
                ├── PinnedAppsPanel.kt        # Widgets-tab pinned apps: PinnedAppsTab + rows (shared AppIconSlot/AppTitleColumn)
                └── SettingsScreen / WidgetsTab / DockSizingTab / BehaviorTab / SettingsComponents
                    # Sidebar layout: nav (Appearance/Widgets/Behavior) + animated content pane
                    # with per-tab header. Tabs collect their OWN state from the repository
                    # (config/favorites/session) — the screen collects nothing, so a media tick
                    # or slider drag never recomposes the whole window. SettingsComponents holds
                    # ALL shared chrome: SettingsCard, SettingsPageHeader, SidebarNavItem,
                    # PrimaryButton/GhostButton, AppearanceDropdown, MinimalistSliderRow,
                    # MinimalistRectSwitch (solid tracks, no gradients), SectionHeader,
                    # IndicatorToggleBox, NotificationToggleBox, WaveformToggleBox,
                    # NotificationGrantRow, BehaviorSwitchRow, WidgetAccordion, TabEntrance —
                    # plus the shared interaction helpers every hoverable control uses:
                    # HoverPressState/rememberHoverPressState, trackHoverPress,
                    # plainClickable (ripple-free), hoverPressScale. Never redeclare per-control
                    # hover/press booleans or ripple-free clickables; use the helpers.
```

---

## 7. Design system

- Palette in `theme/Color.kt`: `NotchDeepBlack`, `NotchSurface`, `NotchCard`,
  `NotchBorder`, accents (`AccentCyan=#38BDF8`, indigo/purple/pink/green/red),
  `TextPrimary/Secondary/Muted`, plus the settings system's solid tokens
  (`SettingsBg/Sidebar/Card/Hover/Elevated/Border/Selected`, `AccentSolid/Soft/Ink`,
  `SwitchTrackOn/Off`, `SwitchThumbOn/Off`). Overlay-local accents live in `MediaWidgets.kt`:
  `LiveAccent=#38BDF8`, `VinylDiscFill`, `VinylRing`, `ProgressGradientStart/End`,
  `WaveformWhite`.
- Settings + onboarding are **gradient-free**: every surface is a flat solid (the old
  switch-track gradients are gone). Shared hover/press/scale micro-interactions live in
  `SettingsComponents.kt` (`HoverPressState`, `trackHoverPress`, `plainClickable`,
  `hoverPressScale`, one 0.78/520 spring) with staggered `TabEntrance` cascades and
  directional `AnimatedContent` tab/step transitions.
- Rounded capsules everywhere, thin borders, dark glass. `LiveAccent` tints live
  media UI. SVG glyphs are tinted via `ColorFilter.tint` (SrcIn) — no hardcoded path data.
- **Level ring**: 20% open at the bottom; full 288°; 0–100; accepts a `Painter`.
- **Vinyl disc** (`VinylDisc`): art clipped to a disc + spindle hub; rotates while
  playing (12s/turn), frozen at its current angle when paused. Used 20dp (pill) and
  56dp (music page) — **both must stay the same visual treatment**. In the expanded
  page the disc slot is an `AnimatedContent` keyed on the artwork bitmap: a track
  change slides the old cover out through the notch's left edge and springs the new
  one in from the left (MusicContent; the page Row clips at the island bounds so the
  slide never draws outside the capsule, and keying on the bitmap means the entrance
  fires exactly when the next cover has decoded). The pill's 20dp disc swaps in place
  (it morphs between surfaces, so no second animation there).
- **MusicWaveform** (`MediaWidgets.kt`): the collapsed pill's faux analyser — capsule
  bars that grow/shrink around their vertical centre (both ends move), bass-weighted,
  sharing one slow loudness envelope. Bar count is derived from the slot width
  (`barPitch` 6dp, 8..40), the clock is capped at 30 updates/s, one coroutine + one state
  write for the whole row, read in the draw scope only. Tint is `WaveformTint.WHITE` or
  `WaveformTint.ALBUM_ART` (`MediaSession.artTint`, falling back to white with no cover).
  Collapsed view only. With the title shown it takes a 7dp band under the 11sp line;
  with `waveformHidesTitle` it owns 18dp of the title's 22dp frame. On pause the bars
  ramp down over ~250ms onto a flat line (`WaveformFlatFraction`) and the clock stops
  entirely, so a paused track costs nothing; on play they climb back to full motion.
  It keeps animating while the settings window has focus — the notch sits *above* the
  menu bar, so nothing ever covers it (pitfall 29).
- **Animation budget**: every continuous pill animation (vinyl, marquee, waveform)
  ticks at `PillAnimationFps` (30) via `delay` + monotonic deltas — never a
  `withFrameNanos` subscription, and never a placement-phase write (pitfall 29).
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
| Settings scrolling is smooth: METAL (skiko 0.8.25) renders the window, so a scroll frame costs ~3.4ms of process CPU instead of ~70ms on the software rasterizer — measured 61fps (vsync-capped) vs 14.5fps; the collapsed pill also stops animating while settings is in front (16.1% → 3.9% of a core) | done (this session) — the "Metal crashes" conclusion was wrong (the forced-SOFTWARE property shadowed the env var, and skiko 0.8.18's Metal really does SIGTRAP; pitfall 28) |
| Now-playing pill + detailed music page + transport | done |
| Transport hit areas are exactly the drawn controls: prev/next = flat 30dp `Box` clickables, play/pause = one 34dp clickable circle (`clip` **before** `clickable` so the hit area is the circle), each with a hover scale (1.16 / 1.12, spring 0.6/380) | done (this session) — measured by a synthetic point sweep on the installed build: prev 774–800, play 803–837, next 844–868 all fire, everything outside is dead (pitfalls 24/25) |
| Expand-into-music + shared-element morph (single-content: `sharedElement`, no mid-flight dual copy) | done (`a02cdc1`, `0c9a9a7`, de-duplicated this session) |
| Idle "curtain" expansion: quickview slides down while pinned apps scale up from behind it, gated off during live music | done (this session) — visual pass pending |
| Blink-free open/collapse: overlay native window keeps a single fixed (expanded) footprint, never resizes during the animation | done (this session) — margin click-swallow handled by the click-through row below |
| Overlay click-through: only the drawn island is a touch target (margin passes input to the app below) — `SetWindowRgn` on Windows, cursor-gated `ignoresMouseEvents` on macOS | done (this session) — macOS live on Apple silicon via a compiled `passthrough_bridge.m` dylib (direct objc_msgSend with a BOOL arg crashes the JVM); Windows path unverified |
| Tray/menu-bar "Always on top" toggle (mirrors Behavior-tab switch, drives `config.alwaysOnTop`) | done (this session) |
| Collapsed-pill waveform: equalizer bars under the title (or alone in the title's frame when `waveformHidesTitle` is on), progress strip yields to it, album-art or white tint, flat line on pause | done (this session) — visual feel needs eyes; the pill ticks at 30fps via `delay`, measured ~9-10% of a core playing (was ~20-25% on display-rate frame clocks) and ~2% paused; the pause ramp verified 0.57→0.00 then the clock stops |
| Music-start jank fixed: artwork decoded at target size, the macOS BT probe no longer spawns `system_profiler` on every track, and the bridge's inline art fetch detached from the poll loop | done (this session) — measured 131ms→66ms cold / 50–60ms→5–11ms per track of process CPU, 0 main-thread stalls (pitfall 31) |
| Pill animations no longer freeze when Settings opens (the notch sits above the menu bar, so it is always visible); they are rate-capped instead | done (this session) — pitfall 29 |
| Volume + audio-battery level rings (both surfaces) | done (`cf9f9be`) |
| Laptop battery ring (collapsed, gated on `hasBattery`) | done (`096b852`) |
| Level-ring glyphs as solid `currentColor` SVG silhouettes, volume ring off Material icons; the Bluetooth indicator (audio → `headphone.svg`, anything else → `bluetooth.svg`) also badges the connected-device count above 1 | done |
| Idle clock in pill (date/time formats) | done |
| Settings tabs (Appearance / Widgets / Behavior) | done |
| Settings revamp: sidebar layout (Appearance/Widgets/Behavior nav + animated content pane with per-tab headers, reset only on Appearance), 16dp solid `SettingsCard` sections with subtitled headers and staggered entrances, gradient-free controls (`MinimalistRectSwitch` now solid tracks), hover/press/scale micro-interactions on every control via shared helpers; onboarding rebuilt around segmented progress, a stage card for the carried notch, 2×2 feature cards and interactive style cards | done — compiles + jars green; the look itself still needs eyes on a live machine |
| Quickview notification indicator: bell + red unread-count badge from the macOS Notification Center store, plus the settings toggle with a compact rectangular **Grant Full Disk Access** button (deep-links the Privacy &amp; Security pane; the helper row disappears once the count reads) | done (this session) — value is `count(*)` on `record`, the store's live ledger behind the Notification Center (adds on delivery, removes on dismiss/Open/Clear; the earlier `delivered = 1` bug referenced a column that never existed → badge stayed "needs FDA"; the `record`-row semantics beats `sum(app.badge)` because badges don't move on dismissal, so they couldn't reflect removals). The residual count after a Clear All is honest: macOS keeps unread-badge apps' notifications (WhatsApp) *and* genuinely persistent rows (Screen Time weekly report) until read/dismissed, same as the Dock badges. A one-time **Send Test Notifications** flow (osascript banners / WinRT `toast.ps1`) was removed by request — the badge is a real NC ledger, not a demo surface. Windows has no store, so its bell stays hidden (`count` null). App version bumped to **3.0.0** |
| Fresh manual launch opens the Settings window alongside the notch; login/boot auto-start stays quietly behind the tray (distinguished by `--quicknotch-launched-at-login`, written into the LaunchAgent/Run-key command) | done (this session) — verified on macOS via CGWindowList on both paths (manual = overlay + settings; marker = overlay only); Windows launch still unverified |
| macOS above-menu-bar placement: overlay parked **flush at the true top of the screen** (level `NSStatusWindowLevel` 25, collection `canJoinAllSpaces|stationary|fullScreenAuxiliary|ignoresCycle`, top-gap = style edge gap) via `MacOverlayWindowPolicy` + the AppKit dylib; AWT would park it under the menu bar (its alwaysOnTop tops out at floating level 3 and placement clamps to the visible area) | done (this session) — **verified live**: `CGWindowList` shows the overlay at `kCGWindowLayer=25`, bounds `y=0`. Off-state (Always-on-top off) drops to level 0 and re-parks below the menu bar. Windows analog is AWT `alwaysOnTop` alone (unchanged) |
| Calendar schedule page in the expanded notch: a third wheel page (after Music/Apps, only while connected) in Google's schedule-view shape -- today's date big on the left (accented), then the next 4 event dates as "d MMM" tiles over count badges (today first, as "Today", when it still has events ahead), gaps skipped; `computeNotchMetrics` claims a 348dp width when the page exists; the fetch is open-ended (timeMin only, no timeMax -- the API has no schedule-mode endpoint, but omitting timeMax *is* schedule semantics: everything upcoming, capped by maxResults=250), because any day window amputated the months-out dates the page promises. The ticker is a notification now: it runs from 2 min before an event until the event ends (no linger), expanding the notch over it consumes it (`GoogleCalendar.dismissTicker`, state-based so a pre-tick dismissal still counts) and lands directly on the calendar page; collapse snaps back to page 0 and the next event re-arms the ticker. The pill badge shows today's still-upcoming events via `calendarUpcomingCount` (running counts, ended does not) and renders in ALL three pill branches -- the clock branch originally skipped it, so the sizer widened the notch for an icon that never drew (pitfall 15 again); the schedule's first tile is today ("Today", accented) when it has events still ahead, then the future dates; Refresh spinner + mutex unchanged. Every date on the expanded page -- today's block included -- is now a link into that exact day in the browser (`googleDayUrl` -> `https://calendar.google.com/calendar/u/0/r/day/<year>/<month>/<day>`, unpadded, matching Google's own documented form; `u/0` because no API reports the browser's account-slot index and `?authuser=` silently falls back to a *different* account when that one isn't signed in). Tiles carry the same hover language as the transport buttons (accent capsule + 1.05 spring) plus an `Open <date> in Google Calendar` semantics label, and the click hops to `Dispatchers.IO` because `AppLauncher.browse` waits on `open` for up to 10s (the same `waitFor` mistake the transport buttons made). Pointer modifiers hang off `interactive` like the dock icons -- a hit-testable node on an alpha-0 page would sit in the hit path of the page beneath it (pitfall 24). Decay is live: `GoogleCalendar.minuteTick` (minute-granular, published from the existing 1s ticker loop, one write per minute change per pitfall 29) feeds the badge, both notch sizers and the page, so an ended event drops out within a minute -- and fetched-vs-displayed reconciles as `fetched = shown + ended`, by design | done (this session) -- stub harness: 9-case green incl. a simulated end-of-today tick; the schedule layout itself needs eyes |
| First-run onboarding: a four-step flow (welcome -> pill style cards -> Google Calendar connect -> pinned apps) that owns the settings window until finished, with the notch preview carried between steps as a shared element and applying choices to the config live; each step skippable; the real overlay does not compose until `config.onboardingDone` flips (verified: no passthrough-armed line while onboarding), and closing the window mid-flow finishes it so the notch always ends up existing; `resetToDefaults` preserves the flag | done (this session) -- compiles, smoke-verified (overlay absent during onboarding); the transitions and step layouts themselves need eyes |
| Google Calendar in the notch: OAuth connect in Widgets, today's events fetched on a 30-min poll + manual refresh, a calendar glyph badged with the day's count in the collapsed pill, and the event title on a ticker from 2 min before to 30 min after | done (this session) — **verified against a local stub of Google's endpoints** (consent redirect -> code -> token -> userinfo -> events, `expires_in: 0` to force the refresh branch): 3 of 5 events kept (cancelled + unnamed "Busy" dropped, all-day kept), all six ticker-window assertions pass, token/event caches written, a 503 on events keeps the last known day with `staleError`, `invalid_grant` clears the token and reports revoked, disconnect drops both. **The real consent screen is unverified** (needs the user's Google login). **The consent UI has since moved to the marketing site** (`quicknotch.app/connect` -> `/oauth/callback` -> loopback), see pitfall 42: `state`/`code_challenge` echo through Google's URL and the callback's loopback hop arrives with `code`+`state` intact (Playwright, production build), but a real Google consent round-trip still needs the user's login **and** `https://quicknotch.app/oauth/callback` registered as an authorized redirect URI on the client |
| macOS Dock presence tied to the Settings window: accessory agent (no Dock icon, no app menu bar) while only the notch runs; Regular + activate while Settings is open — `MacDockPresence` via `setActivationPolicy` + reconcile loop | done (this session) — **verified live**: `lsappinfo` reports `type="UIElement"` notch-only and `type="Foreground"` (Dock icon, in front) with Settings open; closing Settings returns to accessory. Activation is **one-shot on the open transition** — the reconcile loop only re-asserts the policy, so another app keeps focus (verified: TextEdit stays frontmost across ~12 ticks with Settings open, and stays front while media ticks fire with music playing — the presence controller lives in `DisposableEffect(Unit)`, not the composition body, see pitfall 20). Windows analog: overlay built with `WS_EX_TOOLWINDOW | WS_EX_NOACTIVATE` (no taskbar/Alt+Tab slot, no focus steal) — unverified until a Windows run |

**Verification matrix (important!):**
- **Windows desktop (the dev machine)**: fully testable — build, run, dock, settings, wheel paging. Media needs a playing track (browsers/SMTC or a music app).
- **Laptop battery UI**: testable on the macOS dev machine (built-in battery → `SystemBattery` talks through `pmset -g batt`). On the Windows desktop it stays null by design.
- **macOS (this machine)**: now a live test surface — above-menu-bar island placement (`CGWindowList` layer 25, y=0) and the Dock-presence flip (`lsappinfo` UIElement → Foreground) verified live; idle clock + laptop battery ring, volume ring (SVG glyphs), audio-device battery ring (via `system_profiler`), the METAL renderer and the `software-render` fallback (frame-synced scroll bench: 61fps/3.4ms vs 14.5fps/70ms) all verified in-session. Still untestable here: the physical hover/click-through feel at the true top edge (needs a live cursor on this machine), Win32 paths, Windows wheel paging, SMTC seek, and the `WS_EX_TOOLWINDOW` taskbar hiding. The very first pull of this repo may have had no macOS hardware; that is no longer true.
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
  green, smoke-run with `:composeApp:run`, then commit **signed** on the current branch
  (author stays the user; never push — the user pushes).
- Reuse `LevelRing`/`VinylDisc`/`MarqueeText`/`TrackProgress` for any music/battery UI.
- Keep shared-element keys/scope rules intact when touching the morph (see 5.4 + pitfalls).
- Use monotonic clocks (`System.nanoTime()`-based) for extrapolation math; sample stamps
  in ms from the same clock.
- Gate pollers behind `HostPlatform.is...` and make failed probes leave last value.

**DON'T**
- Don't force the Skiko `SOFTWARE` renderer: it costs ~70ms of CPU per settings
  scroll frame (~14fps) versus ~3.4ms on METAL, and the escape hatch is the
  `software-render` marker file, not a build-wide default (pitfall 28).
- Don't set `skiko.renderApi` without checking who else has an opinion: an
  unconditional assignment shadows both the `SKIKO_RENDER_API` env var and the
  marker file, which is how "let's try Metal" silently stayed on CPU.
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
7. **Gradients/brushes must not be rebuilt per frame**: cache brushes per surface at class
   load (see `IslandSurface`, `surface`); the `SYSTEM_ACCENT` style is rebuilt only when
   the OS accent value changes (a rare, user-driven event) and cached per value — no
   per-frame allocation while animating. Never rebuild it per poll tick.
8. **Artwork**: never re-rasterize per poll — cache by content id, downscale to ≤256px.
9. **`AnimatedVisibility` + morph crossing**: the pill must stay visible until `showDock`
   flips while a track is live (`if (musicLive) !showDock else !isExpanded`).
10. **Same-content morphs must use `sharedElement`, never `sharedBounds`**:
    `sharedBounds` *by design* renders BOTH the entering and the exiting content
    inside the travelling frame — its enter/exit only style them, they never
    remove the second copy (bytecode-verified:
    `scaleInSharedContentToBounds()` = `EnterTransition.None +
    ContentScaleTransitionEffect`), so the pill copy and the page copy
    double-draw mid-flight: two covers, two titles (the marquees also scroll
    out of register), rings printed twice — the "duplicate album cover of 2
    sizes and song title of 2 sizes" the user sees while expanding/collapsing.
    The disc/title/rings are the *same content* at different sizes, which is
    exactly `sharedElement`'s contract: only the target content is rendered in
    the transforming bounds — always one cover and one title on screen. The
    earlier `scaleInSharedContentToBounds` attempt (0c9a9a7) reduced the fade
    artifact but kept the dual render; the fix that actually kills it is the
    primitive switch itself (`nowPlayingSharedElement`, section 5.4).
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
17. **Never resize the transparent overlay window during open/collapse** — resizing a
    transparent AWT/Compose window on macOS white-flashes / skips a frame (known Compose
    Desktop + Skiko artifact; jetsniff `compose-multiplatform` #3760, #3420, #4153) and
    reads as a blink. The overlay window now keeps one fixed footprint — the expanded dock
    size — for its whole life (see `NotchOverlayWindow` in `main.kt`); expanding is only
    drawn growth of the island inside it. The margin cost is now mitigated: the window's
    interactive area is clipped to exactly the drawn island by `WindowPassthrough`
    (see 5.10) — `SetWindowRgn` on Windows, cursor-gated `ignoresMouseEvents:` on macOS —
    so the surrounding desktop/app stays click-through; if the native handle can't be
    resolved it fails soft to the old fully-interactive margin. Hover gating is unaffected
    because it checks the real cursor against the island box's coordinates.
    If a future change needs a tighter footprint, the trade-off must be revisited
    deliberately — don't reintroduce a per-open/collapse native resize.
    (Update: the touch target is now the *drawn island* in raw physical px — the
    hover-glow margin stays hover-only, and the density re-multiply that
    double-scaled the interactive area on >100% displays is gone; see 5.10.)
18. **AWT can't park a window above the menu bar and its `locationOnScreen` goes
    stale the moment Cocoa moves the window** (both verified live + in JDK 17
    source). `setAlwaysOnTop(true)` maps to `NSFloatingWindowLevel` (3) — below
    the menu bar's `kCGMainMenuWindowLevel` (24) — and AWT placement clamps to
    the visible frame (y ≥ menu-bar height), so a Dynamic Island always parks
    *under* the menu bar ("below the screen"). Both are fixed by the compiled
    bridge: `NSStatusWindowLevel` (25) + `frame-top-gap` + the Spaces collection
    behavior, re-asserted on a ~700 ms reconcile loop because Compose placement
    and AWT z-order reorders re-park the window 30pt low. Because
    `Window.locationOnScreen` is AWT's *cached* native origin, everything that
    does screen math in AWT terms — passthrough hit-testing, Compose hover gating
    through `localToScreen` — drifts 30pt once the window sits above the menu bar;
    both now read the real CG origin from the bridge (`quicknotch_get_window_cg_origin`),
    and Compose's `localToScreen` result is shifted by that live drift
    (`MacAppKit.screenOriginDelta`). Never reintroduce `locationOnScreen`-based
    screen math in the overlay. Note `androidx.compose.ui.window.LocalWindow` is
    `internal` in CMP 1.7.3 — pass the AWT window down as a parameter instead of
    reaching for the composition local.
19. **Never fire `activateIgnoringOtherApps` from the Dock-presence reconcile
    loop.** The loop re-asserts `setActivationPolicy` every ~700 ms because AWT
    initialises the process Regular and can fight a one-shot accessory flip —
    but calling `[NSApp activateIgnoringOtherApps:YES]` on each of those ticks
    while Settings is open yanks focus back to QuickNotch every ~700 ms, so the
    user can *never* click into another application (the front app snaps back the
    moment they try). Activation must be a **one-shot** request armed only on the
    closed→open transition (`MacDockPresence.pendingActivate`, consumed by the
    next policy pass). Verified: with the fix, `osascript ... "TextEdit" to
    activate` keeps TextEdit frontmost across ~12 reconcile ticks with Settings
    open, while the one-shot at launch still fronts the Settings window.
20. **Never put native side effects in the `application {}` / composable BODY** —
    Compose re-executes a composable body on every state it reads. The Dock
    presence controller was initially started straight from the body
    (`MacDockPresence.start()` + `setSettingsVisible(...)`), so every media
    sample (new `MediaSession` object ≈ every 2s with music playing, plus
    volume/notification ticks) re-ran them: each recomposition re-armed
    `pendingActivate`, the one-shot activation fired ~every second and the app
    stole focus back from whatever the user was using — and the pointer churn
    around those activations also killed the macOS wheel paging on the notch.
    Side effects belong in `DisposableEffect(Unit)`/`LaunchedEffect`. Verified
    by instrumenting the toggles (`pendingActivate=true` fired 4× in 12s before,
    0× after; TextEdit keeps focus while music plays). Note why earlier smoke
    tests missed it: with no media playing the pollers' `StateFlow`s don't
    re-emit (unchanged values), so the body barely recomposed — always test
    focus/presence behaviour **with music live**.
21. **Never `dispatch_sync` onto AppKit's main queue from the AWT EDT** (or
    loop through the EDT to get there). The passthrough's 30ms tick used to
    hop IO → `invokeLater` (EDT) → `dispatch_sync` (main queue) every tick:
    33 EDT→main sync hops per second. AWT's own main-thread upcalls sometimes
    wait on the EDT, so the two threads can end up waiting on each other —
    the macOS "not responding" beachball, most reproducible with music
    playing + the settings window open (max EDT load). macOS native calls
    already marshal onto the main queue inside the dylib, so the EDT hop is
    pointless: macOS `apply()` runs directly on the IO worker; only the rare
    synthetic MOUSE_MOVED re-arm uses `invokeLater`. Same rule for the
    overlay's screen-origin drift poll: `MacAppKit.screenOriginDelta` runs in
    `withContext(Dispatchers.IO)` inside its `LaunchedEffect`. Windows keeps
    its EDT hop only because `SetWindowRgn` is an owner-thread courtesy.
22. **Never keep an `InfiniteTransition` alive when its visual is static** —
    `VinylDisc` used to spin its 60fps transition even while paused (frozen
    angle), and `MarqueeText` ran its scroll even when the text fit, so a paused
    track burned a constant redraw of the overlay. Rotation now accumulates
    real frame deltas inside a `LaunchedEffect(spinning)` (cancelled by the
    pause), and the marquee transition only exists while the text overflows.
    General rule: gate continuous animations on the state that makes them
    visible, and run the ones that stay visible on a capped tick rather than a
    frame-clock subscription (pitfall 29).
24. **A disabled Compose control is still hit-testable — gate the whole modifier.**
   The "only the active page receives input" rule cannot be implemented with
   `enabled = false`: `clickable(enabled = false)` still registers a
   hit-testable node, so an inactive (alpha 0) page keeps occupying the hit
   path of the page composed beneath it. Build the pointer modifier
   conditionally instead (`.then(if (interactive) Modifier.clickable(…) else
   Modifier)`), or pass `onClick = null` where the component already supports
   it (that is how `VinylDisc` drops its clickable). Same rule for
   `pointerInput(enabled = false)` and for `onPointerEvent` hover probes —
   an Enter/Exit probe on an inactive page consumes nothing but still
   participates in the hit path.

25. **Never verify the prev-track button by comparing track names.** On macOS
   the ⏮/previous-track command *restarts* the current track whenever playback
   position > ~3s, and only steps back below that. A synthetic click 20s into a
   track therefore "does nothing" as far as `get name of current track` is
   concerned while the button is working perfectly — the symptom looks
   identical to a dead click area and cost a long debugging detour (it
   masqueraded as a hit-test/z-order problem, and an inactive-page icon
   blocking theory was chased on top of it). Verify with
   `get player position` (expect a reset to <4s) or click within 3s of the
   track start. Same for verification of a media command generally: assert the
   observable that the command actually changes (position, track name, player
   state) rather than assuming a name change means "nothing happened".

26. **The overlay window ignores synthetic scroll events on macOS.** A
   `CGEvent(mouseType: .scrollWheel)` / `CGEvent(scrollWheelEvent…)` posted at
   the island never reaches Compose — the status-level window with
   `ignoresMouseEvents` flipping does not accept injected scroll from a
   non-foreground process. Wheel paging therefore cannot be exercised
   synthetically; it must be tested with a real trackpad/mouse. Do not
   conclude the pager is broken from a synthetic-scroll test.

27. **Settings tabs collect their OWN state; the screen collects none.** The
    settings window recomposed end-to-end on every media heartbeat (a new
    `MediaSession` object every ~2s) and every slider-drag frame because
    `SettingsScreen` read `config`/`favorites`/`liveSession` at the top. Now
    each tab (`DockSizingTab`/`WidgetsTab`/`BehaviorTab`) collects from the
    repository itself, and `NowPlayingStatus` collects the session — a media
    tick only recomposes that tiny row. Keep new settings UI following this
    rule; passing whole-state params back up re-couples the window.
28. **macOS rendering: METAL is the default, and skiko must be new enough for
    it.** The renderer is the single biggest performance lever in this app, and
    it was accidentally left on the CPU rasterizer for a long time. Measured on
    the settings list, one scroll frame costs **~70ms of process CPU on
    SOFTWARE (~14fps, visibly unsmooth) versus ~3.4ms on METAL (vsync-capped
    ~61fps)** — 20x. Two separate mistakes kept it on SOFTWARE, and both are
    easy to repeat:
    - `main()` used to set `skiko.renderApi=SOFTWARE` *unconditionally on
      macOS*, which silently shadowed the `SKIKO_RENDER_API` environment
      variable. Every "let's just try Metal" test ran the CPU rasterizer and
      looked like Metal crashing. The property is now only set when nothing
      else has an opinion, and the chosen backend is logged at startup.
    - Metal on skiko 0.8.18 (CMP 1.7.3's transitive default) really does
      **SIGTRAP at launch (exit 133)**: its `MetalRedrawer.createMetalDevice`
      runs on the AWT EDT and triggers a WindowServer transaction, which macOS
      26/27 rejects with `EXC_BREAKPOINT` / "Must only be used from the main
      thread" (from the crash report's `asi.libsystem_c.dylib`). **0.8.25 fixes
      it**; skiko is pinned in `gradle/libs.versions.toml` and forced for the
      whole `org.jetbrains.skiko` group (skiko + skiko-awt + the per-platform
      runtime jar must all move together, or the JNI layer and the native half
      disagree). OPENGL is not an option: skiko throws
      "MacOS does not support OPENGL rendering API" outright.
      Never downgrade skiko without re-running the settings scroll bench.
    - Escape hatch: because a backend fault is a *native* crash that can happen
      before any UI exists, the fallback must not depend on the settings
      window. Dropping an empty `software-render` file in the app-data dir
      (`AppPaths.configDir`) forces SOFTWARE on the next launch;
      `-Dskiko.renderApi` / `SKIKO_RENDER_API` override both.

29. **Don't animate a notch you think is hidden — and don't trust the frame clock
    to be free.** Two lessons from the same measurement.

    (a) The collapsed pill's disc/marquee used to be *frozen* outright while the
    settings window was open, on the theory that nobody was looking. That is
    wrong for this app: the island sits **above the menu bar** at the true top of
    the screen, so the settings window floats below it and the pill is always
    visible. A user noticed the notch had gone "dead" and asked why. Never
    suppress an animation the user can see.

    (b) The real cost was the frame clock, not the drawing. Three
    `while (true) { withFrameNanos { … } }` loops kept the scene taking
    display-rate frames even though they only *wrote* state occasionally — a
    10 writes/s cap still cost ~16% of a core, because awaiting the frame clock
    subscribes to it. The fix is `delay(PillAnimationStepMillis)` +
    `System.nanoTime()` deltas: same exact motion rate (deltas are real), one
    repaint per tick, ~9-10% instead of ~20-25%, and 2% when the track is
    paused. Third leg of the same stool: never move a pixel with a
    **placement-phase** lambda. `Modifier.offset { x }` re-runs layout on every
    write, so `MarqueeText` now travels via `graphicsLayer { translationX }`.

    General rule from pitfall 22 still holds — gate continuous animation on the
    state that makes it visible (paused, text fits, nothing composed) — but the
    throttle, not the freeze, is the tool for anything the user can see.
30. **Never size a table from a state write that the draw phase reads.** The
   waveform's bar count started life as `onSizeChanged { resolved = count }`
   with `remember(resolved) { WaveformBands(resolved) }` and a draw loop
   `for (index in 0 until resolved)`. Writing `resolved` invalidates the *draw*
   scope immediately, but the `remember` table is only rebuilt on the next
   *recomposition* — so for one frame the loop iterated 20 bands against a
   16-entry table and the app died with `ArrayIndexOutOfBoundsException` on the
   main thread. Resolve anything a table is built from **in composition**
   (`BoxWithConstraints`/`constraints.maxWidth`), pass the count down as a plain
   parameter, and let only values that need no matching allocation (the phase
   clock) be read in the draw scope. When a crash appears only after the ring
   toggles change the slot width, suspect exactly this layout-time skew.

31. **Never do per-track heavy work on the poll thread, and never spawn a probe
    on a state flip.** "The apps stutter when music starts" had three unrelated
    causes, all measured rather than guessed (an EDT stall watcher found *zero*
    main-thread stalls, which is what ruled out the UI entirely):
    - **Artwork**: decode → wrap as a Compose bitmap → unwrap → scale → wrap
      again was 19–49ms of copying per track and 131ms cold. `sourceSubsampling`
      cuts it to a single decode at the size we use (4–10ms warm).
    - **`AudioBattery.poke()` keyed on `musicLive`**: on macOS that is a
      `system_profiler` process launch (~150ms) firing at the exact moment a
      track starts. The 10s poll and the event-driven CoreAudio watcher already
      keep the ring correct, so the musicLive trigger was pure cost; it is gone,
      and `poke()` is coalesced so open/close churn cannot stack spawns either.
    - **The bridge fetched artwork inline**: `AttachMusicArt` ran its
      `osascript` *on the poll callback*, so the whole metadata feed (title,
      state, position) stalled behind an AppleScript on every track change. It
      now dispatches the fetch and re-emits when the art lands, with the in-
      flight guard and a stale-id check so a fast track change cannot stack two
      osascripts or cache art for the wrong track.

    General rules: keep per-track work proportional to the *display* size we
    actually draw, never let a "refresh now" API run on every state transition
    when a poll already covers it, and never block a polling loop on a
    subprocess. Note the visible trade-off of the detached art fetch: a new
    track's cover can arrive one poll late (~100–300ms), so the disc may show
    its blank face for a beat before the art lands.

---

34. **The jpackage runtime is jlink-trimmed, so `com.sun.net.httpserver` is
    not in it.** The OAuth loopback listener was written on `HttpServer`, which
    works under `./gradlew run` (a full JDK) and throws
    `NoClassDefFoundError` in the packaged `.app` — verified with
    `jimage list Contents/runtime/Contents/Home/lib/modules | grep httpserver`,
    which returns nothing. It failed *silently and totally*: the class reference
    was outside `authorize()`'s try, so the coroutine died with
    `SupervisorJob` swallowing it, the state stayed `Connecting` forever, the
    browser never opened, and not one line was logged. Two lessons: keep
    listener/transport setup inside the guarded block (and catch `Error`, not
    just `Exception` — `NoClassDefFoundError` is an `Error`), and take anything
    a jlink image might have dropped down to `java.base`. The loopback is now a
    hand-rolled `ServerSocket` (`serveCallback`) answering a single GET, and it
    is verified *inside the packaged runtime* by running the real consent flow
    from the installed binary and curling the port it logs. Note also that
    `cp -R`/`ditto` silently leave an **empty** app bundle when installing this
    DMG (fcopyfile I/O error on the runtime); `rsync -a` is the only installer
    that has worked. For the same reason, do not trust
    "the app is running" as proof the build is good — check the log file.

35. **The jlink image Compose's jpackage builds has 7 modules, so "use the
    standard library" is not always available.** `java.net.http` and
    `jdk.httpserver` are both absent from the shipped runtime; only
    `java.base`, `java.datatransfer`, `java.desktop`, `java.logging`,
    `java.prefs`, `java.xml` and `jdk.crypto.ec` are there (check with
    `jimage list Contents/runtime/Contents/Home/lib/modules`). A library that is
    ordinary bytecode travels with the jars and always works, which is why the
    calendar's HTTP runs on **Ktor CIO** from `commonMain` and the OAuth loopback
    is a hand-rolled `ServerSocket`. Two habits when adding a dependency here:
    verify it inside the *installed* app rather than under `./gradlew run`, since
    a dependency's own JVM requirements are invisible to the compiler, and do not
    let `commonMain` reach for a desktop-only helper — an `AppLog` call in the
    API layer does not compile, which is the boundary doing its job for free.

37. **Google Calendar's `timeMin`/`timeMax` are RFC3339, not epoch seconds.**
    The day query went out as `timeMin=1790706600` and the stub accepted it, so
    the harness was green while real Google answered 400 every time. The
    symptom was the worst kind: the fetch failed *silently* (a null return),
    the state fell to `Error(network)`, and the user read "not connected" after
    a sign-in that had completely succeeded -- exchange included. Three lessons:
    a stub must validate what the real endpoint documents, not just what it
    consumes; a null that collapses four different failures (HTTP status,
    unreachable, malformed, empty) hides the one you actually have (the fetch
    result is a sealed `EventsResult` now, each logged); and "connected" and
    "the day synced" are different states -- the widget now lands on
    `Connected` the moment tokens exist, with the fetch outcome layered on top
    as `staleError`, so a failing day never again reads as a failed sign-in.
    Refreshes are serialized by a mutex because the button outruns the round
    trip and overlapping refreshes race the token file. (Google's `dateTime`
    always carries seconds, but `10:00+05:30` is valid ISO-8601 the kotlinx
    parser rejects -- `parseEventMillis` normalises rather than dropping the
    event, which is exactly what a stub's `ZonedDateTime.toString()` exposed.)

38. **OAuth consent and token exchange work even when the Calendar API itself
    is disabled for the project** -- the first events read is where it shows,
    as 403 `PERMISSION_DENIED` whose body says "Calendar API has not been used
    in project ... before or it is disabled. Enable it by visiting <url>". The
    body is the diagnosis, so `EventsResult.Http` carries it and the log prints
    it verbatim; a 403 with that shape classifies as `REASON_API_DISABLED`,
    which the widget maps to a string naming the API Library console step.
    Without that, a disabled API reads as "couldn't reach Google" and sends the
    user looking at their router. Console order worth remembering: OAuth
    consent screen -> credentials -> **API Library** (enable Calendar) -> test
    users.

34. **A failed HTTP status is not an empty response.** Porting the calendar to
    Ktor turned a revoked token into an empty calendar: Google's refusal is a
    JSON body with no `items` key, so `?: return emptyList()` reported "the day
    is empty" and would have wiped the pill instead of keeping the last known
    day. `HttpURLConnection` had returned null on a non-2xx, so the swap quietly
    regressed it. Check `response.status.isSuccess()` before parsing and treat
    "could not fetch" and "nothing today" as different answers — the same reason
    token calls return a sealed `OAuthResult` instead of throwing, since a
    refusal's error code is the only useful thing in the response.

36. **Google's token endpoint now demands a `client_secret` from Desktop-app
    clients too.** The documented desktop flow is public-client PKCE with no
    secret, but the endpoint no longer honours it (verified 2026-09-30 with a
    real captured code): the identical exchange without the secret answers
    `invalid_request: client_secret is missing`, and adding the secret Google
    issued for the client returns tokens. Two traps around that error:
    (a) it is NOT evidence of a Web-application client — Google's own gcloud
    client id fails the same way with a fake code, so "client_secret is
    missing" is the endpoint's generic answer for *any grant it cannot match*,
    and a nonexistent client answers `invalid_client` instead. (b) The console
    can be believed: both clients of the same project behaved identically, and
    creating yet another Desktop client changed nothing. What actually settled
    it was capturing a real code (a temporary debug mode logged the callback
    query and the PKCE verifier, no exchange) and replaying the token request
    by hand — the secret was the only variation that worked. So the app sends
    `client_secret` on both token grants, exactly as gcloud ships its own; the
    secret alone authorizes nothing, since every token still needs a live
    user's consent, and PKCE is what binds the exchange to the app's own
    authorization request. Rotate the secret in the console if it leaks beyond
    the repo. Practical method worth keeping: capture, then replay with curl —
    failed token requests do not consume the code, so one real consent buys a
    whole matrix of experiments within its ~10-minute life.

33. **A desktop OAuth client in Google's "Testing" consent status only lets
    *test users* through.** A brand-new client refuses everyone else with
    `access_denied` and the description "The developer hasn’t given you access
    to this app. It’s currently being tested and it hasn’t been verified by
    Google" — no loopback callback, no browser tab the user can read, and
    nothing in the app to distinguish it from pressing Cancel. Verified by
    decoding the `authError` parameter out of the redirect Google *did* serve.
    Fix is in the Cloud console (add the account under OAuth consent screen →
    Test users, or publish the app to External), never in the code. Because the
    symptom is invisible from a packaged app, `datasource/AppLog.kt` mirrors the
    diagnostics into `quicknotch.log` in the app-data dir: stderr is perfect for
    a Gradle run and worthless for a `.app`, and a failure the user can only
    describe is a failure we cannot read.

32. **Arm a derived-state loop on the "not ready yet" path, not the ready one.**
    The calendar ticker resolved "is an event live right now" on a 1s tick that
    was started inside `start()` *after* the "already connected at launch" early
    return — so it only ever existed for a user who had connected before this
    launch. Connecting from settings is the normal case, and that user got the
    day, the badge, and a permanently empty ticker. The loop is now armed before
    the token check, because it resolves against whatever is connected *now*.
    General rule: a loop whose job is to publish derived state must start
    unconditionally and idle cheaply (one `delay` + one equality check), not on
    the branch where its input happens to already exist. Found by a stub harness
    that reproduced the real order (launch disconnected -> connect later) — worth
    remembering that a harness which only tests the happy path with inputs
    pre-populated hides exactly this class of bug.

41. **A Compose resource accessor used without an *explicit import* does not resolve.**
    The string/drawable accessors the resource generator emits (e.g.
    `internal val Res.string.onboarding_back` in `String0.commonMain.kt`) are
    Kotlin *extension properties*. They live in a different file than the code
    that uses them, and Kotlin only finds an extension property through an
    explicit import (or a same-file declaration), not just by being in the same
    module. Symptom: `Res.string.onboarding_back` compiles for sibling
    accessors that sit behind explicit
    `import com.starkdroid.quicknotch.generated.resources.onboarding_finish`
    lines at the top of `OnboardingScreen.kt`, while a *newly added* string
    referenced the same way fails with a lone `Unresolved reference
    'onboarding_back'` -- and renaming the string does not help, because the
    name is not the problem. Every onboarding accessor is explicitly imported,
    and the missing import is the whole bug. Rule: when you add a resource and
    reference it, add the matching `import com.starkdroid.quicknotch.generated.resources.<name>`
    in the same edit. Cost about 45 minutes and a full clean-build rule-out.

43. **A Desktop OAuth client cannot register an https redirect URI at all — the
    console does not render the field.** The hosted consent flow (pitfall 42) is
    therefore only reachable with a **Web application** client, and that type is
    confidential, so `client_secret` becomes mandatory rather than optional. The
    type is forced by the console UI, not chosen: a Desktop app client accepts
    *any* `http://127.0.0.1:<port>` implicitly (which is why the old loopback
    flow needed no registered URI and no secret) and offers nowhere to add one.
    Two things follow that are easy to get wrong:
    - `GoogleCalendar.kt` had the client type described three different ways
      across comments (a "Desktop-app client id" next to the constant, a
      `REASON_CLIENT_TYPE` doc claiming Web, and pitfall 36 recording that
      creating another Desktop client changed nothing). Only the console settles
      it — read the type off the client's detail page, never off the repo.
    - Once Web-typed, `classifyTokenRefusal`'s "wants a secret" branch no longer
      means *you are the wrong client type*. It means the secret is mistyped,
      rotated, or not yet propagated. The old wording sent us looking at the
      client type when the answer was the secret.

    **Google exempts loopback origins from the HTTPS rule** ("Redirect URIs must
    use the HTTPS scheme, not plain HTTP. Localhost URIs (including localhost IP
    address URIs) are exempt from this rule"), which is what makes local
    testing of the hosted flow possible at all. While the site is undeployed
    `DEFAULT_WEB_BASE` **is** `http://localhost:5173` — no env var needed, because
    testing this flow is the current job and a flag to remember is one more way
    to test a stale origin. `PRODUCTION_WEB_BASE` sits beside it as the constant
    to swap in at go-live; `QUICKNOTCH_WEB_BASE` still overrides both.

    The port is part of the registered URI, so the dev server must be started
    with `npm run dev:oauth` (`--strictPort`). Plain `npm run dev` falls through
    to 5174 when 5173 is busy and every flow then fails as
    `redirect_uri_mismatch` for a reason unrelated to the code — the same class
    of bug as the double-scaled hit-test rect in pitfall 17, where the wrong
    value was invisible on the developer's own machine.

    **The redirect URI must never be a constant in two places.** The desktop
    sends `client_id` + `redirect_uri` to `/connect`, and the page prefers those,
    falling back to `location.origin + '/oauth/callback'` — same-origin rather
    than a hardcoded URL, because the correct URI *is* wherever the page is being
    served from (5173 dev, 4173 preview, production in future). Echoing the wrong
    one either fails as `redirect_uri_mismatch` or, worse, authorises against a
    URI the app will not exchange against. Console changes can take **5 minutes
    to a few hours** to take effect, so a URI that "should" work failing
    immediately usually means propagation, not configuration.

    Corollary on the *refusal* path: Google bounces a decline to the site's
    callback, so if that page only ever handled a successful `code`, the app sat
    out its full 5-minute timeout on every cancelled consent. It forwards
    `error`/`error_description` to the loopback too, because
    `classifyConsentRefusal` is what distinguishes "user pressed Cancel" from
    "consent screen still in Testing" (pitfall 33) — a distinction that only
    exists app-side. A refusal with *no* usable `state` cannot be handed back at
    all, so the page says so instead of jumping at a guessed port.

44. **A consent page's inert button is a symptom, not a styling nit.** The
    connect page's error branch rendered "Try opening Google again" bound to a
    `consentUrl` that is only ever set on the *success* branch — so the button
    did nothing at all, and it was the only prominent action on the page. Any
    page whose controls are built inside a parse branch needs its error branch
    to offer a way out that does not depend on that branch's state; here the only
    correct exit is back through the app, since the app is what mints the
    loopback port.

42. **The calendar's OAuth consent UI lives on the website now; the token
    exchange cannot, and must not, move with it.** The site's `web/` is a fully
    static SvelteKit build (`adapter-static`, `prerender = true`, no server
    endpoints at all) and Google's token endpoint demands a `client_secret`
    from every client type (pitfall 36) -- so the exchange has to stay in the
    desktop app, which is where the secret already is. What moved to the web is
    the *consent UI*: `GoogleCalendar.authorize()` now opens
    `https://quicknotch.app/connect?client_id&redirect_uri&port&state&scope&
    access_type&prompt&code_challenge&code_challenge_method` instead of
    building the Google URL itself. The flow, end to end:
    1. Desktop mints PKCE verifier/challenge and binds a loopback
       `ServerSocket` (pitfall 34 -- still hand-rolled, still `java.base`).
    2. The **loopback port rides inside `state`**: base64url of
       `{"p":<port>,"s":"<nonce>"}`. This is the load-bearing detail. The
       consent round-trip now passes through a website, so Google's echo is the
       only thing that can tell the callback which port to jump home to, and the
       desktop verifies the *entire* echoed string against what it sent --
       `serveCallback` refuses a code whose state does not match
       (`state_mismatch`), it never exchanges a code from an unrelated attempt.
    3. `/connect` builds the Google authorize URL, carrying the same
       `code_challenge` and `state` through unchanged.
    4. Google bounces to `https://quicknotch.app/oauth/callback` (must be an
       authorized redirect URI on the client). That page decodes the port out of
       `state` and does `location.assign('http://127.0.0.1:<port>/oauth?'+qs)`.
    5. The loopback server accepts that GET, reads `code` + `state`, replies
       with the small `CONSENT_PAGE`, and `exchangeCode` runs in-process.

    Why a **top-level navigation** for the hand-off and not a `fetch`: a
    cross-origin POST/fetch from `https://quicknotch.app` to
    `http://127.0.0.1:<port>` is blocked or needs CORS + Private Network
    Access preflight headers, and those preflights also make the loopback
    server answer more than once. A browser navigating the tab to loopback is
    the one direction every browser allows unconditionally, and it is the same
    mechanism the direct-Google flow always used. Verified end to end in
    Playwright against the production build: `state`/`code_challenge` echo
    byte-for-byte through Google's URL, and the callback's navigation arrives at
    the loopback with `code` and `state` both intact.

    Two site-side rules that are easy to regress: `/connect` and
    `/oauth/callback` must **not** render the marketing `Nav` -- its section
    anchors (`#widgets`, `#themes`...) resolve to nothing on those routes and the
    prerenderer hard-fails the build with `handleMissingId` (the slim sticky
    bar in `connect/+page.svelte` replaced it), and `Seo.svelte` takes no props
    (its title is a module-level const), so `<Seo title="..."/>` is a type
    error. `?standby=1` on `/connect` renders the page with the consent button as
    the trigger instead of a timer; it exists so the flow can be reviewed and
    screenshotted without starting a real sign-in, and the desktop never sends
    it.

## 11. Git workflow

- Branch: `cursor/macos-compose-adaptation`; work here, commit, `git push`.
- Commits are the user's: GPG-sign every commit (`git commit -S`), author stays the
  user, and never push — the user pushes.
- One logical change per commit; message = imperative summary matching recent history
  (e.g. "Reorder pinned apps by dragging instead of arrow buttons").
- Before committing: `compileKotlinDesktop` + `desktopJar` green + smoke run.
- Static analysis is detekt 1.23.8 (last stable line; 2.x is alpha-only), wired as
  `:composeApp:detekt` and running inside `check`. It must be green before every
  commit — it guards import hygiene (`UnusedImports` is on), naming, and
  complexity for NEW code. Pre-existing findings are grandfathered in
  `config/detekt/baseline.xml`; after a deliberate fix run `detektBaseline` to
  re-cut it. Config lives in `config/detekt/detekt.yml` with three project
  tunings: `MagicNumber` off (dp/sp literals are idiomatic Compose),
  `FunctionNaming` exempts `@Composable` (PascalCase), and constants accept
  `UPPER_SNAKE` or PascalCase (the palette tokens). No auto-formatter is
  configured (a formatter would churn every file) — formatting stays review.
- Detekt does not see resources, so the manual sweep stays for those: drop
  unused `Res.string`/`Res.drawable` entries and confirm zero `Brush.*Gradient`
  in settings/onboarding files.
- Check `git status` before every `git stash` — concurrent sessions share this branch,
  and stashing blind sweeps up work you did not author (recovering a conflicted pop
  cost a real scare: the foreign WIP silently deleted import lines in non-conflicting
  regions). When in doubt, ask before resolving a conflict either way.
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
- (this session → HEAD): the overlay's native window stopped resizing during open/collapse —
  it now keeps one fixed (expanded) footprint for its whole life and the island is drawn
  growth inside it, killing the macOS transparent-window resize flash that read as a blink
  (pitfall 17). The clockwise `isWindowExpanded`/`onWindowExpandedChange` plumbing that used
  to drive that resize is gone. The fixed window's click-swallowing margin is clipped away
  by `WindowPassthrough` (5.10) — the island is the only touch target; a tray/menu-bar
  "Always on top" toggle mirrors the Behavior-tab switch.
- (this session → HEAD): a fresh manual launch opens the Settings window so the app's
  presence is obvious; login/boot auto-start passes `--quicknotch-launched-at-login`
  (from `StartupManager`, written into the LaunchAgent/Run-key command) and stays
  quietly behind the tray. The notification indicator became a live "notifications in
  the Notification Center" count: `count(*)` on the usernoted `record` table (adds on
  delivery, removes on dismiss/clear), replacing both the `sum(app.badge)` and the
  `delivered = 1` dead-column attempts.
- (this session → HEAD): the settings helper for the notification indicator stopped
  pretending to be a demo surface — the **Send Test Notifications** flow (osascript
  banners / `toast.ps1`) is gone, leaving only the compact rectangular **Grant Full
  Disk Access** button (deep-links to Privacy &amp; Security, hidden once the count
  reads). Windows no longer fakes a session count; its bell stays hidden. App version
  jumped **1.0.0 → 3.0.0** across the feature line.
- (this session → HEAD): a macOS platform-compat study (§5.11) confirmed the two
  integration gaps — AWT's `alwaysOnTop` tops out at floating level 3 (below the
  menu bar) and its `locationOnScreen` is a cached origin — and the
  `passthrough_bridge.m` dylib grew from click-through to the full island
  controller: `quicknotch_set_window_level`, `set_collection_behavior`,
  `set_frame_top_gap`, `get_menu_bar_height`, `get_window_cg_origin`,
  `set_activation_policy`, `activate_app` (all marshalled on AppKit's main queue).
  The new `MacAppKit.kt` fronts them for the overlay's **above-menu-bar placement**
  (`MacOverlayWindowPolicy`: level 25 + Spaces collection + real-top top-gap,
  toggle-aware — Always-on-top off parks below the menu bar at level 0; ~700ms
  reconcile loop) and the Settings-gated **Dock presence** (`MacDockPresence`:
  accessory while only the notch runs, Regular + activate with Settings open;
  ~700ms reconcile because AWT initialises the process Regular). Passthrough's
  hit-test and the overlay's collapse gate both read the real CG origin
  (`MacAppKit.screenOriginDelta` shifts `localToScreen`), because AWT screen math
  is 30pt low once the island parks above the menu bar. Windows mirrored the
  "no OS chrome while only the notch runs" rule with
  `WS_EX_TOOLWINDOW | WS_EX_NOACTIVATE` on the overlay hwnd.
- (this session → HEAD): fixed the Dock-presence loop stealing focus while
  Settings was open — it was calling `activateIgnoringOtherApps` on every ~700 ms
  reconcile tick, so no other app could keep focus. Activation is now a one-shot
  armed on the closed→open transition (`MacDockPresence.pendingActivate`); the
  loop re-asserts only the policy (pitfall 19). Notch above-menu-bar placement and
  the accessory↔Regular flip are unchanged and still verified live.
- (this session → HEAD): fixed the *second* focus bug — the Dock-presence
  controller was being started + `setSettingsVisible(true)` imperatively from the
  `application {}` body, and since that body re-runs on every media/volume/
  notification tick, the one-shot activation was re-armed and fired ~every
  second while music played (it also disrupted the notch's wheel paging, which
  the churn was the only suspect for after the top-edge placement). Both now
  live in `DisposableEffect(Unit)`/`LaunchedEffect` (pitfall 20); instrumented
  proof: `pendingActivate` fired 4×/12s before → 0× after, TextEdit keeps focus
  with music live, and a synthetic scroll over the expanded dock flips pages
  0→1→0 with the island parked at the true top. The Settings quickview-indicators
  card's notification cell lost its hardcoded "3" badge — the live count only
  belongs on the island.
- (this session → HEAD): the morph's mid-flight duplicates were finally killed —
  the four morphing keys (disc/title/rings) moved from `sharedBounds` to
  `sharedElement` (`nowPlayingSharedElement`). sharedBounds keeps BOTH contents
  inside the travelling frame by design (`scaleInSharedContentToBounds` is only
  a style effect, bytecode-verified), so the pill copy and page copy double-drew
  at 2 sizes; sharedElement renders only the target content — one cover, one
  title (pitfall 10 rewritten). Verified: repeated hover expand/collapse cycles
  with music live run exception-free; the visual seamlessness itself still needs
  eyeballs on a live machine.
- (this session → HEAD): the settings freeze + low-fps scrolling were tracked to
  three compounding causes, all fixed. (1) The passthrough's 30ms tick rode
  IO → EDT → `dispatch_sync` (main queue) — 33 sync hops/s that deadlocked
  against AWT's own EDT-hopping upcalls ("not responding" with music + settings
  open); macOS now applies off the EDT (pitfall 21), and the overlay's
  `screenOriginDelta` poll moved to `Dispatchers.IO` too. (2) `VinylDisc` ran a
  60fps `InfiniteTransition` even while paused and `MarqueeText` even when the
  text fit — each invalidation is a full-window repaint (a full CPU raster back
  then, see pitfall 28); both now animate only when their visual actually
  moves.
  (3) `SettingsScreen` collected config/favorites/liveSession at the top, so
  every media heartbeat and slider-drag frame recomposed the whole window
  (pitfall 23) — tabs now collect their own state. The same session also split
  the 1870-line `FloatingNotchOverlay.kt` into root + six focused files
  (`QuickView`, `DockContent`, `MediaWidgets`, `NowPlayingMorph`, `OverlayHover`,
  `NotchMotion`), moved all shared settings chrome into `SettingsComponents`, and
  split `WidgetsTab` into `WidgetsTab` + `PinnedAppsPanel` with the two app rows
  deduped onto shared `AppIconSlot`/`AppTitleColumn` pieces. Metal was re-tested
  as a renderer escape hatch (SKIKO_RENDER_API=METAL) and still SIGTRAPs at
  startup (exit 133) — SOFTWARE stays, per the existing macOS-only runtime
  override.
- (this session → HEAD): the invisible click-swallowing band around the
  collapsed notch was traced to the published touch target, not to a mystery
  window. Two defects: the target was the *hover* box (island + 10dp glow,
  deliberate hover comfort) instead of the drawn island, and it was multiplied
  by `density` again after `localToWindow` had already produced physical px —
  double-scaling the interactive area on every display above 100% (invisible
  on the scale-1.0 dev Mac). The native target is now the drawn island
  (physical px, no re-multiply; Windows inflates the region by 2px, macOS
  compares in logical points via the graphics-config scale with its 4px pad),
  and the collapse gate uses the same island footprint so it can never
  disagree with the native gate. Hover expansion still starts at the pill
  (probe-verified: on-pill hover expands, just-outside hover now exits and
  lets the click-through through). Windows region coordinates were never
  live-verifiable from this Mac.
- (this session → HEAD): the expanded music page's transport buttons passed
  clicks through to the apps below — the published touch target was FROZEN at
  the collapsed pill. `onGloballyPositioned` hands back a stable
  `LayoutCoordinates` instance, so the `LaunchedEffect(islandCoordinates)`
  keyed on it ran exactly once and never republished as the island grew; the
  native gate then ignored everything outside the stale collapsed rect —
  every expanded control (transport!) was click-through. Publishing moved
  inline into the position callback, which fires on every layout change
  (rect-logging proof: collapsed 472×68 → expanded 712×168 at density 2, and
  both play/pause click directions now toggle the OS player). The synthetic
  MOUSE_MOVED re-arm is also delayed ~40ms past the async AppKit flip — an
  immediate fire lost the enter intermittently. Live verified on this Mac at
  density 2: the scale conversion (gate ÷ graphics-config scale, publish
  without re-multiply) is exercised for real.
- (this session → HEAD): the expanded music page's volume ring became the mute
  toggle — one click flips the OS device mute (`SystemVolume.toggleMute`,
  fire-and-forget on IO + immediate re-read poke), the ring empties to level 0
  with a dimmed glyph while muted, and its contentDescription flips between the
  new `music_mute`/`music_unmute` strings. `SystemVolume` now answers
  `<percent>,<muted>` from ONE spawn per poll (macOS AppleScript concatenation,
  Windows `volume.ps1` `GetState()` + `-Action mute|unmute` writes via the
  already-declared `IAudioEndpointVolume.SetMute`); the collapsed pill's ring
  stays non-interactive. Verified on macOS: ring click flips `output muted`
  true/false live. Windows mute write compiles but is unverified (no Windows
  machine here).
- (this session → HEAD): the expanded page's vinyl got a track-change swap —
  `AnimatedContent` keyed on the artwork bitmap inside the shared-element slot:
  the old cover slides out through the notch's left edge (MusicContent's Row now
  clips at the island bounds so the slide never draws outside the capsule) and
  the new cover springs in from the left the moment its bitmap decodes. Keying
  on the bitmap (not the content id) is what makes the entrance wait for the
  art; repeated publishes for the same track reuse the cached instance, so the
  2s heartbeat never re-triggers the swap. The pill's 20dp disc stays an
  in-place swap. Track changes under a held-open expanded notch run
  exception-free; the visual feel itself needs eyeballs on a live machine.
- (this session → HEAD): the transport buttons got three fixes. (1) The macOS
  `send()` ran `ProcessBuilder.waitFor(2s)` ON the calling thread — every
  press blocked the Compose UI thread for the osascript spawn duration; the
  command is now fire-and-forget on the controller's IO scope (Windows already
  was). (2) The play/pause icon is optimistic: `pendingPlay` flips it the
  instant the button is pressed and reconciles when the session state changes
  (or after 3s if a command was lost) — the OS player reacts through the 2s
  bridge poll, and without the flip the icon sat on the old state the whole
  window. (3) The buttons stopped being Material `IconButton`s: their 48dp
  min-touch-target inflation overflowed the 30/34dp slots symmetrically, so
  the drawn buttons and the hit geometry disagreed (measured: prev clickable
  -9..39dp, next 57..105dp row-local, both overflowing the 98dp row; clicks
  near an edge landed in the neighbour's zone or nowhere) — and
  `LocalMinimumInteractiveComponentEnforcement provides false` does NOT stick
  in this material3 version (bounds still 96×96px @2x). They are plain
  sized-`Box` clickables (`TransportButton`, hit area = drawn area) now.
  Verified live: NEXT/PREV change the OS track and PLAY toggles both
  directions at the drawn button centres.
- (this session → HEAD): the vinyl swap was tuned to read as a slow record
  conveyor — travel is 3× the disc width (the cover visibly exits past the
  island's left clip), the exit runs 460ms with the fade delayed to its last
  third, and the enter is a soft 0.72/130 spring with a matched 0.85 scale-in;
  everything animates offset/alpha/scale only (graphicsLayer-backed, no
  relayout mid-swap).
- (this session → HEAD): **the settings window was never slow in its own code —
  it was rendering on the CPU rasterizer.** Measured with a frame-synced scroll
  bench inside the app (one `LazyListState.scrollBy` per display frame, so the
  rate is bounded by the pipeline exactly like a real wheel fling): **~70ms of
  process CPU per frame and 14.5fps on SOFTWARE, versus ~3.4ms and 61fps on
  METAL** — the same widgets, the same code. `main()`'s measure/layout is cheap
  (scroll p95 ≈ 150–270µs); essentially all of the frame was rasterization.
  Two long-standing mistakes kept the app on SOFTWARE: the renderer property
  was set unconditionally on macOS, which silently shadowed
  `SKIKO_RENDER_API` and made every "just try Metal" run a no-op; and Metal on
  skiko 0.8.18 genuinely does SIGTRAP on macOS 26/27
  (`MetalRedrawer.createMetalDevice` on the AWT EDT trips the WindowServer
  main-thread assertion). skiko is now pinned to 0.8.25 for the whole
  `org.jetbrains.skiko` group, macOS defaults to METAL, the chosen backend is
  logged at startup, and a `software-render` marker file in the app-data dir
  remains the escape hatch — deliberately not UI-dependent, because a backend
  fault is a native crash that can happen before any window exists (pitfall 28).
  OPENGL is not an option: skiko rejects it outright on macOS. The renderer
  change also made the overlay cheaper, so the second half of the work spent
  the newly available headroom on wasted frames: the collapsed pill's disc and
  marquee used to redraw at display rate even with the settings window in
  front, and `pillAnimationsLive(settingsOpen, expanded)` held them while
  settings was open and the notch was collapsed (16.1% → 3.9% of a core with a
  track playing). *Superseded the same day by the entries below: the notch is
  never actually covered by the settings window, so the freeze made a visible
  island look dead; the animations are rate-capped instead.* App-data path resolution moved into `AppPaths` so
  the startup backend check can read the marker before `FavoritesRepository`
  exists (pitfall 29).
- (this session → HEAD): the transport controls got a hover affordance and the
  "only the active page receives input" rule was fixed for real. Prev/next and
  the play circle scale up on hover (1.16 / 1.12, spring 0.6/380) and the
  play/pause circle is clipped *before* its clickable, so the hit area is
  exactly the drawn circle (one surface: icon + background). Every pointer
  modifier inside `WidgetPages` — dock icons, transport buttons, play circle,
  mute ring, vinyl disc (`onClick = null`) — is now built conditionally on
  `interactive`, because a disabled clickable is still hit-testable and an
  inactive (alpha-0) page kept occupying the hit path of the page below it
  (pitfall 24). `MacNowPlayingController.send` also got the mandated
  `destroyForcibly` after its 4s wait. Verified on the installed DMG by
  synthetic point sweep: prev 774–800, play 803–837, next 844–868 pt all
  fire and everything outside is dead; play toggles both directions, next
  changes track, prev resets the position. The long detour that got us here
  is worth remembering: the "dead" prev button was **Music's own ⏮ semantics**
  (restart when elapsed > ~3s), which made a working button look broken, and
  no `CGEvent` scroll of any flavour reaches this overlay window, so the
  wheel pager can't be driven synthetically (pitfalls 25/26).

- (this session → HEAD): the collapsed pill grew a **waveform** — a lane of capsule
  bars stacked under the title, each growing from its vertical centre, replacing the
  edge progress strip while the toggle is on (collapsed view only; the expanded music
  page is untouched, and never renders bars). It is honestly *state-driven*, not an
  FFT: `MediaRemote`/SMTC publish metadata only, and reading the system mix would mean
  a ScreenCaptureKit stream plus a Screen Recording grant — so the bars come from two
  slow sines per band plus a shared loudness envelope, bass-weighted, every parameter
  hashed deterministically from the band index. Cost discipline: one coroutine and one
  state write for the whole row (read in the draw scope, `shimmer`-style), capped at
  30 updates/s instead of display rate, and frozen on pause like the disc. Measured 13.7% of a core playing
  with bars versus 16.1% before (hiding the 4Hz strip pays for the lane); 1.5% paused.
  Tint is `WaveformTint.WHITE` or `ALBUM_ART`, the latter coming from `dominantArtTint`
  — a coarse population×chroma histogram run **once per track on the poller's worker
  thread** and cached with the bitmap, so composition never samples pixels; a missing
  cover falls back to white. The first attempt sized the band table from an
  `onSizeChanged` write and died with `ArrayIndexOutOfBoundsException` the moment a
  ring toggle changed the slot width (pitfall 30). Settings gets an indicator cell
  whose preview *is* the component (frozen, 7 bars) plus a colour dropdown that only
  appears while the toggle is on.

- (this session → HEAD): the waveform learned to swallow the title. With
  `widgets.waveformHidesTitle` on, the collapsed live pill is just disc +
  analyser + level rings, and the bars take 18dp of the title's 22dp frame
  instead of the 7dp band they share with the song name (settings gets a
  switch row in the waveform section, next to the tint dropdown). The marquee
  also stopped moving pixels through the *placement* phase: `Modifier.offset { }`
  re-runs layout on every write, so `MarqueeText` now travels via
  `graphicsLayer { translationX }`.
- (this session → HEAD): **the settings-open animation freeze was a mistake and
  the frame clock was the cost.** The notch lives above the menu bar, so the
  settings window never covers it — freezing the pill's disc/marquee/waveform
  while settings had focus just made the island look dead to a user who could
  see it perfectly well. That gate (`pillAnimationsLive`) is gone. The real
  saving was elsewhere: three `while (true) { withFrameNanos { … } }` loops kept
  the whole scene taking display-rate frames no matter how rarely they wrote —
  throttling the writes to 10/s still cost ~16% of a core, proving the
  *subscription* was the cost. All three animations now tick on
  `delay(PillAnimationStepMillis)` + `System.nanoTime()` deltas at
  `PillAnimationFps` (30), which keeps the motion rate identical (real deltas)
  and repaints once per tick: ~9-10% of a core playing (was ~20-25%), ~2%
  paused, and the pill stays alive with settings open (pitfall 29).

- (this session → HEAD): the waveform now *settles* instead of freezing. On pause
  the bars ramp down over ~250ms onto an even line across the middle of the lane
  (`WaveformFlatFraction`), which is what a real analyser does when the signal
  stops and the only honest way to read "no audio"; on play they climb back to
  full motion. The clock stops entirely once the line is reached, so a paused
  track costs nothing at all (measured floor unchanged at ~2%), and the ramp
  itself is a `presence` scalar the draw blends between the flat line and the
  dynamic shape — no per-bar state, still one write per tick.

- (this session → HEAD): the settings page's reset action moved out of the
  Appearance tab and into the top bar, beside the About icon and sharing its
  chrome (32dp square, elevated fill, hairline border, 8dp radius). It is shown
  only on the tab it actually affects — the defaults it restores are the
  notch's look, size and clock, so offering it on Widgets or Behavior would be a
  lie. The action now lives in `SettingsScreen` (which owns the bar and the
  repository); `DockSizingTab` keeps its own subscription so a slider drag still
  recomposes only that tab. *Verified by compile and layout only: synthetic
  clicks do not reach normal (non-always-on-top) windows in this environment —
  no Accessibility permission — so the click itself needs a human.*

- (this session → HEAD): the device-battery ring briefly lost its device mark
  (both battery rings were unified onto the fill silhouette, and `headphone.svg`
  / `bluetooth.svg` were deleted as unused). Restored: the connected-device ring
  draws the device mark again — `headphone` for an audio stack, `bluetooth` for
  anything else — and its settings cell is named "Bluetooth Battery". The ring
  also badges the connected-device count when more than one is around, reusing
  the same badge component the notification count already uses. (An audio-vs-BT
  distinction is *not* a second toggle: one "Bluetooth Battery" switch covers
  both.)

- (this session → HEAD): settings + onboarding visual overhaul, all solid surfaces
  (no gradients anywhere in the window): `SettingsScreen` moved from top tab pills
  to a 232dp sidebar (nav + per-tab animated headers + directional tab transitions,
  reset only on Appearance, spring-in About dialog); every tab section became a 16dp
  `SettingsCard` with subtitled headers and staggered `TabEntrance` cascades;
  `MinimalistRectSwitch` lost its gradient tracks; all hover/press/scale feedback
  consolidated onto shared helpers (`HoverPressState`/`trackHoverPress`/
  `plainClickable`/`hoverPressScale`) instead of per-control booleans; onboarding
  rebuilt around segmented progress, a stage card for the carried notch, 2×2 feature
  cards and hover-lift style cards. Follow-up audit removed the dead `Flat*` tokens,
  ~20 unused imports and 5 dead strings (`dock_sizing_header`, `widgets_header`/`_desc`,
  `calendar_today_count`, `calendar_ticker_label`); no detekt/ktlint exists, so the
  pre-commit sweep stays manual (§11). File-size verdict: `SettingsComponents`
  (~1000 lines) stays whole — it is one concern (settings chrome) with section
  banners, so a split would scatter it; same for the untouched `MediaWidgets`/
  `GoogleCalendar`. The look itself still needs eyes on a live machine.

- (this session → HEAD): static analysis adopted — detekt 1.23.8 (last stable;
  2.x is alpha-only and 1.23.8's Gradle API still loads under the Gradle 8.9
  wrapper) via the version catalog, running as `:composeApp:detekt` inside
  `check` over `commonMain` + `desktopMain` (the KMP layout is set explicitly;
  generated resources under `build/` excluded). Raw first run: 740 findings
  (441 of them `MagicNumber`); project tunings in `config/detekt/detekt.yml`
  (`MagicNumber` off, `@Composable` exempt from `FunctionNaming`, PascalCase
  constants allowed) plus a `detektBaseline` grandfather the rest, so the gate
  fails only on new issues — `UnusedImports` stays on and already paid for
  itself (37 dead imports across 10 files the manual sweep had missed). Same
  pass fixed the safe findings instead of baselining them: stale Ktor-move
  endpoint consts in `GoogleCalendar`, dead Win32 consts, `MayBeConst` ×2,
  `GWL_EXSTYLE` → `gwlExStyle`, dropped the unused `windowHeightPx` param, all
  9 wildcard imports expanded, trailing newlines everywhere. Deliberately left
  in the baseline: complexity debt, fail-soft broad catches, empty no-op
  overrides, `PRODUCTION_WEB_BASE` (KDoc-referenced), `onClose`/`wakeSignal`
  (signature/channel-drain). No formatter (would churn every file).

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
| Idle curtain | quickview slide 360/620ms ×3 height, fade 420ms; dock entry `IdleDockEntryScale=0.82` @ `IdleDockOrigin` (0.5,0.08) on `idleDockRevealSpring` 0.92/150 | overlay |
| Vinyl swap | exit slide ×3 width 460ms (fade delayed 220ms/240ms) + scale 0.85; enter spring 0.72/130 ×3 width + scale-in, fade 260ms @120ms | `DockContent` |
| Transport hover | prev/next scale 1.16, play circle 1.12, spring 0.6/380 | `MediaWidgets` |
| Waveform | 30 updates/s tick, `barPitch` 6dp (8..40 bars), 7dp band under an 11sp title (18dp when the title is hidden), 2 sines/band + shared envelope, pause ramp 250ms to a 12% flat line | `MediaWidgets` |
| Pill animation tick | `PillAnimationFps` 30 via `delay` + nanoTime deltas (never `withFrameNanos`) | `NotchMotion` |
| `ArtworkMaxPx` | 256 | `MediaController` |
| Poll cadence | volume 2s, audio-battery 1s presence (+8s live probe), system-battery 5s | media pollers |
| nowMs tick | 250ms while PLAYING | overlay |
| Page step | 30 dp of wheel travel | `OverlayPager` |

---

*Keep this doc honest: when a fact changes, update it in the same commit. When you
discover a new gotcha, add it under section 10.*