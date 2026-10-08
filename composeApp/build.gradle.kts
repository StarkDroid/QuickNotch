import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.detekt)
}

// Static analysis (detekt): analysis-only rules, no auto-formatting —
// formatting changes would churn every file, so style stays enforced by the
// pre-commit sweep (§11) until a formatter is adopted deliberately. The KMP
// source layout is not the JVM default the plugin assumes, so the analysed
// roots are set explicitly; generated Compose resources under build/ are
// excluded. Findings that predate the adoption are grandfathered in
// config/detekt/baseline.xml — `detekt` fails only on NEW findings.
detekt {
    toolVersion = libs.versions.detekt.get()
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    baseline = file("$rootDir/config/detekt/baseline.xml")
    source.setFrom(
        files(
            "$projectDir/src/commonMain/kotlin",
            "$projectDir/src/desktopMain/kotlin"
        )
    )
}

version = "3.0.0"

// Host platform, used to gate platform-only JVM opens and launch flags (the
// bridge tasks below derive their own copies inside onlyIf for the same check).
val hostOsName = System.getProperty("os.name").lowercase()
val hostIsMac = hostOsName.contains("mac") || hostOsName.contentEquals("darwin")
val hostIsWindows = hostOsName.contains("windows")

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
            // HTTP. Ktor rather than HttpURLConnection/java.net.http for one
            // reason that is not taste: the jlink image Compose's jpackage
            // builds has no java.net.http and no jdk.httpserver (only java.base
            // and a handful of others), so the JDK options are simply not in
            // the shipped app -- and a missing module is a NoClassDefFoundError
            // at the first call, which is exactly how the OAuth loopback broke
            // (pitfall 34). CIO is ordinary bytecode, so it ships with the jars
            // and works on every target. Being multiplatform is the other half
            // of the reason: the network layer lives in commonMain.
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.kotlinx.datetime)
        }
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.jna)
            implementation(libs.jna.platform)
            // slf4j-api rides in transitively (every Ktor module) with no
            // provider, which printed "No SLF4J providers were found" on
            // every launch. The NOP binding satisfies the lookup with a
            // no-op logger — no behaviour change, three warning lines gone.
            runtimeOnly(libs.slf4j.nop)
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

// macOS overlay click-through + window/Dock policy: a tiny dylib
// (`passthrough_bridge.m`) that drives `[NSWindow setIgnoresMouseEvents:]`,
// the above-menu-bar notch level/placement (`setFrameTopGap`), and the app's
// Dock/activation policy on AppKit's main queue. Window passthrough and
// MacAppKit load it with JNA and call the plain C exports, which is far more
// reliable than firing objc_msgSend straight from JVM land (void-return /
// BOOL-arg calls SIGTRAP on Apple silicon). Only built on macOS hosts.
val compileMacPassthroughBridge by tasks.registering(Exec::class) {
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
        "-dynamiclib",
        "-framework", "Foundation",
        "-framework", "AppKit",
        "-framework", "QuartzCore",
        "passthrough_bridge.m",
        "-o", project.file("src/desktopMain/resources/macos/passthrough-bridge.dylib")
    )
}

// macOS entitled-context artwork fetcher: `artwork_bridge.m` is a tiny dylib
// exposing one C entry point (`qn_artwork_get`) that runs a single full-access
// `MRMediaRemoteGetNowPlayingInfo` query — the only context that sees artwork
// bytes is an entitled host, so `artwork-driver.pl` loads this dylib into
// `/usr/bin/perl` via DynaLoader. The driver script is the source of truth in
// `native/macos` (like the Windows .ps1 files) and is copied into resources;
// the dylib is compiled here. Only built on macOS hosts; without it the
// artwork fetcher stays silent and the Music.app AppleScript path remains.
val compileMacArtworkBridge by tasks.registering(Exec::class) {
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
        "-dynamiclib",
        "-framework", "Foundation",
        "artwork_bridge.m",
        "-o", project.file("src/desktopMain/resources/macos/artwork-bridge.dylib")
    )
}
val syncMacArtworkDriver by tasks.registering(Copy::class) {
    from("native/macos") {
        include("artwork-driver.pl")
    }
    into("src/desktopMain/resources/macos")
}

tasks.named("desktopJar") {
    dependsOn(syncWindowsBridge, compileMacNowPlayingBridge, compileMacBtWatchBridge, compileMacPassthroughBridge, compileMacArtworkBridge, syncMacArtworkDriver)
}
tasks.named("desktopProcessResources") {
    dependsOn(syncWindowsBridge, compileMacNowPlayingBridge, compileMacBtWatchBridge, compileMacPassthroughBridge, compileMacArtworkBridge, syncMacArtworkDriver)
}

// AppKit writes a session saved-state file at launch and prints one
// "ApplePersistenceIgnoreState: ..." notice to stderr while doing it — JVM
// platform noise, not ours. AppKit reads -Key value pairs straight from the
// process argv (NSArgumentDomain), so passing its suppressor as an
// application argument quiets it for `run`, where the console is visible.
// The packaged .app has no console, so it doesn't need the flag. The compose
// plugin registers `run` after this script evaluates, so match it live.
if (hostIsMac) {
    tasks.matching { it.name == "run" }.configureEach {
        // doFirst, not configuration time: the compose plugin rewrites this
        // task's args after the script evaluates, so an append made here would
        // be overwritten before execution.
        (this as JavaExec).doFirst {
            args("-ApplePersistenceIgnoreStateQuietly", "YES")
        }
    }
}

compose.resources {
    packageOfResClass = "com.starkdroid.quicknotch.generated.resources"
}

compose.desktop {
    application {
        mainClass = "com.starkdroid.quicknotch.MainKt"

        // Open the AWT implementation packages so WindowPassthrough can reach
        // the native window handle and clip its hit target to the island:
        // java.awt for the private Component.peer field, sun.lwawt(.macosx) for
        // CPlatformWindow.ptr (the NSWindow), sun.awt.windows for
        // WComponentPeer.getHWnd. Each open is added only where the package
        // actually exists — a Windows-only open on a macOS host (or vice
        // versa) prints "WARNING: package ... not in java.desktop" on every
        // launch. The opens apply to `run` and the packaged app alike.
        jvmArgs += listOf("--add-opens", "java.desktop/java.awt=ALL-UNNAMED")
        if (hostIsMac) {
            jvmArgs += listOf(
                "--add-opens", "java.desktop/sun.lwawt=ALL-UNNAMED",
                "--add-opens", "java.desktop/sun.lwawt.macosx=ALL-UNNAMED"
            )
        }
        if (hostIsWindows) {
            jvmArgs += listOf("--add-opens", "java.desktop/sun.awt.windows=ALL-UNNAMED")
        }

        // Renderer selection is handled in `main()`: an explicit
        // -Dskiko.renderApi or SKIKO_RENDER_API wins, then the
        // `software-render` marker file, then METAL — the ~20x-faster default
        // (agents.md pitfall 28). Never set skiko.renderApi here: an
        // unconditional assignment shadows both the env var and the marker
        // and silently parks the settings window on the CPU rasterizer.

        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Msi, TargetFormat.Dmg)
            packageName = "QuickNotch"
            vendor = "StarkDroid"
            packageVersion = project.version.toString()
            description = "Floating notch and dock app launcher for Windows and macOS"
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

// Skiko is pinned to a version whose macOS Metal backend actually works (see
// the `skiko` entry in gradle/libs.versions.toml and pitfall 28 in agents.md).
// Every artifact in the group has to move together: skiko, skiko-awt and the
// platform runtime jar the Compose plugin pulls in, otherwise the JNI layer
// and the native half disagree.
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.skiko") {
            useVersion(libs.versions.skiko.get())
            because("macOS hardware rendering requires the fixed Metal backend")
        }
    }
}
