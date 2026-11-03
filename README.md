# QuickNotch

<div align="center">

![QuickNotch Banner](https://raw.githubusercontent.com/StarkDroid/QuickNotch/main/banner.png)

**A floating notch and dock that pins apps so you can launch them from anywhere on Windows and macOS.**

[![Build & Release](https://github.com/StarkDroid/QuickNotch/actions/workflows/release.yml/badge.svg)](https://github.com/StarkDroid/QuickNotch/actions)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.10-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose Multiplatform](https://img.shields.io/badge/Compose_Multiplatform-1.7.3-4285F4?logo=jetpackcompose&logoColor=white)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![Windows](https://img.shields.io/badge/Windows-10%20%7C%2011-0078D6?logo=windows&logoColor=white)](https://microsoft.com/windows)
[![macOS](https://img.shields.io/badge/macOS-12%2B-000000?logo=apple&logoColor=white)](https://www.apple.com/macos)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

</div>

---

## What it does

QuickNotch lives as a small pill at the top-centre of the screen. Hover it to expand a dock of pinned apps, click an icon to launch, then keep working. The same Jetpack Compose UI runs on both platforms; only scanning, icons, launch, and login items differ.

---

## Features

- **Floating notch** — Collapsed pill that morphs into a dock, anchored to the top-centre of the screen. It stays perfectly still while it expands and collapses (on macOS it sits above the menu bar, flush against the true top of the screen).
- **Dock fisheye wave** — Cosine-squared magnification and lift that track the cursor exactly; the wave radius is derived from icon size automatically.
- **Pin any app** — Search installed apps or browse for a custom executable / `.app` bundle. Reorder and remove from Settings.
- **Native icons**
  - Windows: Win32 Shell jumbo / extra-large icons from `.exe` and `.lnk` files.
  - macOS: `.icns` via `sips`, with Quick Look (`qlmanage`) as fallback.
- **Installed-app discovery**
  - Windows: Start Menu, Desktop, system tools, and Registry uninstall entries.
  - macOS: `/Applications`, `/System/Applications`, Utilities, and `~/Applications`.
- **Launch from anywhere**
  - Windows: `cmd` / UWP AppsFolder / Desktop browse.
  - macOS: `open` for bundles, files, and URLs.
- **Start at login** — Windows Run registry key, or a macOS LaunchAgent (`com.starkdroid.quicknotch`).
- **Tray / menu bar** — Show or hide the notch, open Settings, reset defaults, quit.
- **Settings** — Magnification, icon size, notch geometry, always-on-top, collapse-on-launch, labels.

Pinned apps and config are stored as JSON:

| Platform | Location |
| :--- | :--- |
| Windows | `%APPDATA%\QuickNotch\` |
| macOS | `~/Library/Application Support/QuickNotch/` |

---

## Architecture

- **Language**: Kotlin 2.1.10 (Compose Multiplatform Desktop JVM)
- **UI**: Jetpack Compose 1.7.3 (Material 3) on both platforms — no SwiftUI or WinUI
- **Concurrency**: Kotlinx Coroutines (`kotlinx-coroutines-swing`)
- **Native**: JNA 5.16.0 for Win32; Kotlin + built-in macOS tools (`open`, `sips`, `qlmanage`, LaunchAgents)
- **Persistence**: Kotlinx Serialization JSON

```
QuickNotch/
├── composeApp/
│   ├── build.gradle.kts
│   └── src/desktopMain/kotlin/com/starkdroid/quicknotch/
│       ├── model/
│       │   ├── AppItem.kt
│       │   ├── NotchConfig.kt
│       │   └── FavoritesRepository.kt
│       ├── datasource/
│       │   ├── HostPlatform.kt
│       │   ├── InstalledAppsScanner.kt          # facade
│       │   ├── WindowsInstalledAppsScanner.kt
│       │   ├── MacInstalledAppsScanner.kt
│       │   ├── AppIconLoader.kt
│       │   ├── Win32ShellIconLoader.kt
│       │   ├── MacAppIconLoader.kt
│       │   ├── AppLauncher.kt
│       │   ├── StartupManager.kt                # facade
│       │   ├── WindowsStartupManager.kt
│       │   └── MacStartupManager.kt
│       ├── ui/
│       │   ├── FloatingNotchOverlay.kt          # notch + fisheye dock
│       │   ├── OverlayGeometry.kt               # footprint metrics + top-centre placement
│       │   ├── SettingsScreen.kt
│       │   └── NoiseGenerator.kt
│       ├── theme/
│       └── main.kt                              # tray + overlay + settings windows
├── gradle/libs.versions.toml
├── web/                                         # SvelteKit marketing site (prerendered, static)
└── …
```

---

## Getting started

### Prerequisites

- **JDK 17+** (Eclipse Temurin or Oracle recommended; Homebrew OpenJDK 17 also works)
- **Windows 10/11 (x64)** or **macOS 12+** (Apple Silicon and Intel)

### Run from source

```bash
git clone https://github.com/StarkDroid/QuickNotch.git
cd QuickNotch
./gradlew :composeApp:run
```

On first launch you get a default pin set (Explorer / Terminal / browsers on Windows; Finder, Safari, Terminal, Calculator, and similar on macOS). Hover the pill, or use the tray / menu bar extra to open Settings.

### Package for the current OS

```bash
./gradlew :composeApp:packageDistributionForCurrentOS
```

Outputs land under `composeApp/build/compose/binaries/main/`:

| OS | Formats |
| :--- | :--- |
| Windows | `.msi`, `.exe` |
| macOS | `.dmg`, plus `app/QuickNotch.app` from `createDistributable` |

```bash
./gradlew :composeApp:createDistributable
open composeApp/build/compose/binaries/main/app/QuickNotch.app   # macOS
```

On newer macOS releases, Skiko's Metal backend can crash when creating transparent overlay windows, so `main()` forces `-Dskiko.renderApi=SOFTWARE` at runtime on macOS only to keep the notch stable. Other platforms keep Skiko's default GPU renderer (DIRECT3D on Windows) — forcing the software pipeline app-wide is what made the full-size Settings window lag while the notch stayed smooth.

---

## Releases and updates

Every merged pull request that touches the app becomes a release. The workflow
in `.github/workflows/release.yml` plans the next version with
`.github/scripts/changelog.mjs`, builds the Windows installers and two macOS
DMGs (Apple silicon + Intel), and publishes them together with:

- **`changelog.json`** — the cumulative, user-facing history the app reads from
  GitHub and shows in **Settings → Changelog**. It is fetched at runtime and
  cached locally, never bundled into a build.
- **`SHA256SUMS.txt`** — checked by the in-app updater before it installs.

Versioning starts at **2.0.0** and is driven by labels on the merged PR:

| Labels | Bump |
| :--- | :--- |
| `release:major`, `major`, `breaking` | major |
| `release:patch`, `patch`, `fix`, `bug` | patch |
| anything else, or no label | minor |

A PR that changes only `web/`, the docs or CI merges without producing a build.
The local build defaults to `2.0.0`; CI passes the published number with
`-PappVersion=x.y.z`, and the running binary reports it in **Settings →
Changelog**.

While the PR is still open, `.github/workflows/changelog-preview.yml` runs the
same planner and rewrites a fenced **Changelog preview** block at the bottom of
the description — the version that will ship, the bump, and the exact sentences
users will read. Relabel the PR or edit its body and the block follows; the
author's own description is never touched, and the preview says plainly when a PR
does not touch the app and therefore ships nothing.

The app updates itself: it downloads the package for the current platform in the
background, then a detached script waits for the JVM to exit after **Restart &
update**, installs the MSI (same upgrade UUID, higher version) or rsyncs the
DMG's bundle into place, and relaunches. A build started with `./gradlew run`
has no `jpackage.app-path`, so self-update is unavailable there and the button
opens the GitHub release page instead.

---

## Marketing site

The landing page lives in `web/` as a separate SvelteKit workspace. It is built
independently of the Gradle build and produces a fully static, prerendered site
with scroll-driven animation (GSAP, Three.js, Lenis).

```bash
cd web
npm install
npm run dev      # dev server
npm run build    # static output in web/build/
```

See [`web/README.md`](web/README.md) for the architecture, performance budget,
SEO setup and conventions.

---

## License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.
