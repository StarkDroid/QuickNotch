import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.util.Properties

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

// The release workflow owns the real number: it passes `-PappVersion=x.y.z`
// computed from the published changelog, so CI never mutates a tracked file.
// A local build falls back to the 2.0.0 series so the settings window and the
// updater compare against something meaningful.
val appVersion = providers.gradleProperty("appVersion").getOrElse("2.0.0")

// Neither half of the Google OAuth client is in this repository. Both are read
// from the environment (what CI uses) or from a maintainer's ignored
// `local.properties`, then written into a generated source file. A build
// without them compiles and runs; Calendar then says the build has no client
// rather than opening a consent screen it cannot finish.
//
// The client id is public in OAuth's model (it travels in every consent URL),
// but it names this project's client, so it is injected too: a fork should
// bring its own rather than inherit one it cannot use.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.isFile) file.inputStream().use { load(it) }
}

fun oauthSetting(property: String, environment: String): String =
    (providers.gradleProperty(property).orNull
        ?: providers.environmentVariable(environment).orNull
        ?: localProperties.getProperty(property))
        .orEmpty()
        .trim()

val googleClientId: String = oauthSetting("googleClientId", "QUICKNOTCH_GOOGLE_CLIENT_ID")
val googleClientSecret: String = oauthSetting("googleClientSecret", "QUICKNOTCH_GOOGLE_CLIENT_SECRET")

version = appVersion

// `AppVersion.VERSION` is generated rather than hardcoded in a string resource:
// the updater has to compare the running version with a release tag, and a
// hand-edited string drifts from what the installer was actually built as.
val generateAppVersion by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/appVersion")
    val current = appVersion
    inputs.property("version", current)
    outputs.dir(outputDir)
    doLast {
        val packageDir = outputDir.get().asFile.resolve("com/starkdroid/quicknotch")
        packageDir.mkdirs()
        packageDir.resolve("AppVersion.kt").writeText(
            """
            package com.starkdroid.quicknotch

            /**
             * The version this binary was built as. Injected by the
             * `generateAppVersion` Gradle task from `project.version`; never
             * edit by hand.
             */
            internal object AppVersion {
                const val VERSION: String = "$current"
            }
            """.trimIndent() + "\n"
        )
    }
}

// `OAuthConfig` holds the Google client the Calendar flow signs in with. The
// secret is injected here from the build environment instead of being written
// into a tracked file, so the repository can be public and the history shows no
// credential.
val generateOAuthConfig by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/oauthConfig")
    val clientId = googleClientId
    val clientSecret = googleClientSecret
    inputs.property("clientId", clientId)
    inputs.property("clientSecret", clientSecret)
    outputs.dir(outputDir)
    // The output carries a credential: keep it out of the build cache and any
    // cache upload.
    outputs.cacheIf { false }
    doLast {
        fun literal(value: String): String = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("$", "\\$")
        val packageDir = outputDir.get().asFile.resolve("com/starkdroid/quicknotch")
        packageDir.mkdirs()
        packageDir.resolve("OAuthConfig.kt").writeText(
            """
            package com.starkdroid.quicknotch

            /**
             * The Google OAuth client this binary signs in with. Injected by the
             * `generateOAuthConfig` Gradle task; never edit by hand. The client id
             * is public, the secret comes from the build environment and is not
             * in the repository.
             */
            internal object OAuthConfig {
                const val CLIENT_ID: String = "${literal(clientId)}"
                const val CLIENT_SECRET: String = "${literal(clientSecret)}"
            }
            """.trimIndent() + "\n"
        )
    }
}

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

// Generated version source: registered after the KMP block so the
// `desktopMain` source set exists, and wired into every Kotlin compile.
kotlin.sourceSets.getByName("desktopMain").kotlin.srcDir(layout.buildDirectory.dir("generated/appVersion"))
kotlin.sourceSets.getByName("desktopMain").kotlin.srcDir(layout.buildDirectory.dir("generated/oauthConfig"))
tasks.matching { it.name.startsWith("compileKotlin") }.configureEach {
    dependsOn(generateAppVersion)
    dependsOn(generateOAuthConfig)
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

// macOS fullscreen-game detector: `gamewatch_bridge.m` exposes one C entry
// point (`qn_fullscreen_game`) that answers "is a non-shell app covering the
// main display right now, and which one?" from CGWindowList — the only signal
// available without Screen Recording or Accessibility permission. Called
// in-process through JNA (see `MacGameWatch`), so polling costs a few
// microseconds and spawns nothing. Only built on macOS hosts.
val compileMacGameWatchBridge by tasks.registering(Exec::class) {
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
        "-framework", "CoreGraphics",
        "gamewatch_bridge.m",
        "-o", project.file("src/desktopMain/resources/macos/gamewatch-bridge.dylib")
    )
}

tasks.named("desktopJar") {
    dependsOn(syncWindowsBridge, compileMacNowPlayingBridge, compileMacBtWatchBridge, compileMacPassthroughBridge, compileMacArtworkBridge, compileMacGameWatchBridge, syncMacArtworkDriver)
}
tasks.named("desktopProcessResources") {
    dependsOn(syncWindowsBridge, compileMacNowPlayingBridge, compileMacBtWatchBridge, compileMacPassthroughBridge, compileMacArtworkBridge, compileMacGameWatchBridge, syncMacArtworkDriver)
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
        // `software-render` marker file, then METAL — the ~20x-faster default.
        // Never set skiko.renderApi here: an unconditional assignment shadows
        // both the env var and the marker and silently parks the settings
        // window on the CPU rasterizer.

        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Msi, TargetFormat.Dmg)
            packageName = "QuickNotch"
            vendor = "VelocityApps"
            packageVersion = project.version.toString()
            description = "Floating notch and dock app launcher for Windows and macOS"
            copyright = "© 2026 VelocityApps. All rights reserved."
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
// the `skiko` entry in gradle/libs.versions.toml and the renderer notes in
// `main.kt`).
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
