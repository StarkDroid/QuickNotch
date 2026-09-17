# QuickNotch

<div align="center">

![QuickNotch Banner](https://raw.githubusercontent.com/StarkDroid/QuickNotch/main/banner.png)

**A sleek, ultra-responsive macOS Dynamic Island & Dock floating app launcher for Windows 10 & 11.**

[![Build & Release](https://github.com/StarkDroid/QuickNotch/actions/workflows/release.yml/badge.svg)](https://github.com/StarkDroid/QuickNotch/actions)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.10-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose Multiplatform](https://img.shields.io/badge/Compose_Multiplatform-1.7.3-4285F4?logo=jetpackcompose&logoColor=white)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![Platform](https://img.shields.io/badge/Platform-Windows%2010%20%7C%2011-0078D6?logo=windows&logoColor=white)](https://microsoft.com/windows)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

</div>

---

## ✨ Features

- **🏝️ Dynamic Island Aesthetic**: Elegant floating notch pinned to your top screen edge (or configurable to bottom, corners, etc.) with smooth morphing physics.
- **🌊 Authentic macOS Dock Fisheye Magnification Wave**: Continuous mathematical cosine-squared bell curve magnification with fluid elevation and scaling as your cursor glides across docked icons.
- **⚡ Native Win32 Shell Icon Extraction**: High-performance multi-tier cached icon extraction directly from Windows `.exe`, `.lnk` shortcuts, and system utilities.
- **🔍 Instant Installed Apps Scanner**: Scans Start Menu shortcuts, desktop apps, system utilities, and Windows Registry (ARP) to effortlessly pin applications.
- **🛠️ Standalone Customizer Settings**:
  - Customize screen edge positions (Top Center, Bottom Center, Corners).
  - Fine-tune dock magnification peak scale (1.0x - 2.5x) and wave radius.
  - Reorder, add, or remove pinned applications with custom executable file chooser.
  - Windows auto-start on boot toggle via native Windows Registry integration.
  - Configurable tooltips, icon sizes, and launch behaviors.
- **🔔 Windows System Tray Integration**: Minimal footprint background tray icon with instant controls to toggle visibility, open settings, or reset defaults.

---

## 🏗️ Architecture & Tech Stack

QuickNotch is built with modern desktop architecture principles:

- **Language**: Kotlin 2.1.10 (Kotlin Multiplatform Desktop)
- **UI Framework**: Jetpack Compose Multiplatform 1.7.3 (Material Design 3)
- **Concurrency**: Kotlinx Coroutines 1.10.1 (`kotlinx-coroutines-swing`)
- **Native Windows Interop**: JNA 5.16.0 (`jna`, `jna-platform` for Win32 Registry and Shell integration)
- **Data & Serialization**: Kotlinx Serialization JSON

```
QuickNotch/
├── composeApp/
│   ├── build.gradle.kts
│   └── src/desktopMain/kotlin/com/starkdroid/quicknotch/
│       ├── model/
│       │   ├── AppItem.kt              # App item data entity & serialization
│       │   ├── NotchConfig.kt          # Notch positions & configuration parameters
│       │   └── FavoritesRepository.kt  # Persistent settings & pinned apps storage
│       ├── datasource/
│       │   ├── InstalledAppsScanner.kt # Win32 Start Menu, Desktop & Registry ARP scanner
│       │   ├── Win32ShellIconLoader.kt # Native icon extraction & monogram generator
│       │   ├── AppLauncher.kt          # Windows process launcher & shell executor
│       │   └── WindowsStartupManager.kt# Windows registry autostart manager
│       ├── ui/
│       │   ├── FloatingNotchOverlay.kt # Dynamic Island & Dock Fisheye Magnification wave
│       │   └── SettingsScreen.kt       # Dedicated Material 3 settings & app manager
│       ├── theme/
│       │   ├── Color.kt                # Modern dark Apple / Fluent color palette
│       │   ├── Typography.kt           # Clean typography scale
│       │   └── Theme.kt                # Compose Material 3 theme wrapper
│       └── main.kt                     # Application entry point, tray & window overlay
├── gradle/
│   └── libs.versions.toml              # Version catalog
├── settings.gradle.kts
└── build.gradle.kts
```

---

## 🚀 Getting Started

### Prerequisites

- **Java Development Kit (JDK)**: JDK 17 or higher (Temurin or Oracle recommended)
- **Operating System**: Windows 10 or Windows 11 (x64)

### Building from Source

1. Clone the repository:
   ```bash
   git clone https://github.com/StarkDroid/QuickNotch.git
   cd QuickNotch
   ```

2. Run the application in development mode:
   ```bash
   ./gradlew run
   ```

3. Build native Windows installers (`.msi` and `.exe`):
   ```bash
   ./gradlew packageDistributionForCurrentOS
   ```
   Installers will be generated under `composeApp/build/compose/binaries/main/msi/` and `composeApp/build/compose/binaries/main/exe/`.

---

## 📜 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
