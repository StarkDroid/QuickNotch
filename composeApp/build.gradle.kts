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
//  • Windows: a PowerShell SMTC bridge — no compilation, just a copy. It runs
//    on both Windows PowerShell 5.1 and PowerShell (Core) 7.
val syncWindowsBridge by tasks.registering(Copy::class) {
    from("native/windows/smtc.ps1")
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

tasks.named("desktopJar") {
    dependsOn(syncWindowsBridge, compileMacNowPlayingBridge)
}
tasks.named("desktopProcessResources") {
    dependsOn(syncWindowsBridge, compileMacNowPlayingBridge)
}

compose.resources {
    packageOfResClass = "com.starkdroid.quicknotch.generated.resources"
}

compose.desktop {
    application {
        mainClass = "com.starkdroid.quicknotch.MainKt"
        jvmArgs += listOf("-Dskiko.renderApi=SOFTWARE")

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
