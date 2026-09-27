import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

version = "1.0.0"

kotlin {
    jvm("desktop")

    sourceSets {
        val desktopMain by getting

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.materialIconsExtended)
            implementation(compose.components.resources)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.jna)
            implementation(libs.jna.platform)
        }
    }
}

// The media ("now playing") feature ships a small native helper per platform.
// Both live in `native/` as the single source of truth and are materialized
// into the desktop resource root (then the jar) by these tasks, so the
// repository never needs to store build artifacts:
//
//  • macOS: an Objective-C MediaRemote bridge compiled on the host with `cc`.
//    The MediaRemote framework is dlopen'd at runtime, so no private headers
//    are required to build. Only built on macOS hosts; on other hosts the
//    resource is simply absent and the Mac controller stays silent.
//  • Windows: PowerShell bridges — no compilation, just a copy. `smtc.ps1`
//    reads the SMTC session and `volume.ps1` reads the master volume; both
//    run on Windows PowerShell 5.1, the engine the app launches at runtime.
val syncWindowsBridge by tasks.registering(Copy::class) {
    from("native/windows")
    into("src/desktopMain/resources/windows")
}
val compileMacNowPlayingBridge by tasks.registering(Exec::class) {
    onlyIf {
        val os = System.getProperty("os.name").lowercase()
        os.contains("mac") || os.contentEquals("darwin")
    }
    doFirst {
        project.file("src/desktopMain/resources/macos").mkdirs()
    }
    workingDir = project.file("native/macos")
    commandLine(
        "cc",
        "-O2",
        "-mmacosx-version-min=12.0",
        "-fobjc-arc",
        "-framework", "Foundation",
        "nowplaying_bridge.m",
        "-o", project.file("src/desktopMain/resources/macos/nowplaying-bridge")
    )
}

// macOS Bluetooth/audio change watcher: CoreAudio default-output listeners +
// bluetoothd connect notifications that let the audio-battery poller probe the
// stack the instant a device connects (see `btwatch_bridge.m`). Same build
// shape as the now-playing bridge above; only built on macOS hosts.
val compileMacBtWatchBridge by tasks.registering(Exec::class) {
    onlyIf {
        val os = System.getProperty("os.name").lowercase()
        os.contains("mac") || os.contentEquals("darwin")
    }
    doFirst {
        project.file("src/desktopMain/resources/macos").mkdirs()
    }
    workingDir = project.file("native/macos")
    commandLine(
        "cc",
        "-O2",
        "-mmacosx-version-min=12.0",
        "-fobjc-arc",
        "-framework", "Foundation",
        "-framework", "CoreAudio",
        "btwatch_bridge.m",
        "-o", project.file("src/desktopMain/resources/macos/btwatch-bridge")
    )
}

tasks.named("desktopJar") {
    dependsOn(syncWindowsBridge, compileMacNowPlayingBridge, compileMacBtWatchBridge)
}
tasks.named("desktopProcessResources") {
    dependsOn(syncWindowsBridge, compileMacNowPlayingBridge, compileMacBtWatchBridge)
}

compose.resources {
    packageOfResClass = "com.starkdroid.quicknotch.generated.resources"
}

compose.desktop {
    application {
        mainClass = "com.starkdroid.quicknotch.MainKt"

        // Open the AWT peer packages so WindowPassthrough can resolve the
        // native window handle (sun.lwawt.LWWindowPeer.getNSWindowPtr on macOS,
        // sun.awt.windows.WComponentPeer.getHWnd on Windows) and clip its hit
        // target to the island. Both apply to `run` and the packaged app.
        jvmArgs += listOf(
            "--add-opens", "java.desktop/sun.lwawt=ALL-UNNAMED",
            "--add-opens", "java.desktop/sun.awt.windows=ALL-UNNAMED"
        )

        // Renderer selection is platform-specific and handled in `main()`:
        // macOS forces SOFTWARE at runtime because Skiko's Metal backend can
        // crash on transparent overlay windows. Everywhere else we keep Skiko's
        // default GPU renderer (DIRECT3D on Windows). A global SOFTWARE override
        // here made the full-size Settings window rasterize on the CPU every
        // frame — that is what made settings interactions laggy while the small
        // notch overlay stayed smooth.

        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Msi, TargetFormat.Dmg)
            packageName = "QuickNotch"
            vendor = "StarkDroid"
            packageVersion = project.version.toString()
            description = "Dynamic Island & Dock floating app launcher for Windows and macOS"
            copyright = "© 2025 StarkDroid. All rights reserved."
            windows {
                iconFile.set(project.file("quicknotch.ico"))
                upgradeUuid = "8e97f012-3a5c-4f8e-9d22-b2d99d3e8e1a"
                dirChooser = true
                menuGroup = "QuickNotch"
                shortcut = true
                menu = true
            }
            macOS {
                iconFile.set(project.file("quicknotch.icns"))
                bundleID = "com.starkdroid.quicknotch"
                dockName = "QuickNotch"
                appCategory = "public.app-category.utilities"
            }

            buildTypes.release.proguard {
                isEnabled = false
            }
        }
    }
}
