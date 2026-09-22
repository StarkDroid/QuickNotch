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
