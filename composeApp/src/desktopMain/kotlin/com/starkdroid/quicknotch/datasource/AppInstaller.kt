package com.starkdroid.quicknotch.datasource

import java.io.File

/**
 * Replaces the running install with a downloaded package.
 * jpackage upgrades its own app (MSI upgrade UUID, DMG bundle rsync), and a process cannot
 * replace files it runs from, so a detached script waits for this PID and installs.
 */
object AppInstaller {

    /** Set only by a jpackage launcher; a Gradle-run JVM has no path and must never replace itself. */
    private val packagedPath: String? =
        System.getProperty("jpackage.app-path")?.takeIf { it.isNotBlank() }

    val canSelfUpdate: Boolean get() = packagedPath != null

    fun launch(packageFile: File): Boolean = runCatching {
        require(packageFile.isFile) { "downloaded package is missing" }
        val log = File(AppPaths.configDir, "update.log")
        log.parentFile?.mkdirs()
        if (HostPlatform.isMac) launchMac(packageFile, log) else launchWindows(packageFile, log)
        AppLog.line("update: installer launched for ${packageFile.name}")
        true
    }.getOrElse { failure ->
        AppLog.line("update: could not start installer: ${failure.message}")
        false
    }

    private fun launchMac(packageFile: File, log: File) {
        val bundle = appBundle() ?: error("not running from an app bundle")
        val script = File(packageFile.parentFile, "quicknotch-update.sh")
        script.writeText(MAC_SCRIPT)
        script.setExecutable(true, true)
        ProcessBuilder(
            "/bin/sh",
            script.absolutePath,
            ProcessHandle.current().pid().toString(),
            packageFile.absolutePath,
            bundle.absolutePath
        )
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.appendTo(log))
            .start()
    }

    private fun launchWindows(packageFile: File, log: File) {
        val exe = packagedPath ?: error("no packaged executable path")
        val script = File(packageFile.parentFile, "quicknotch-update.ps1")
        script.writeText(WINDOWS_SCRIPT)
        ProcessBuilder(
            "powershell.exe",
            "-NoProfile",
            "-ExecutionPolicy", "Bypass",
            "-WindowStyle", "Hidden",
            "-File", script.absolutePath,
            "-ProcessId", ProcessHandle.current().pid().toString(),
            "-Package", packageFile.absolutePath,
            "-Exe", exe
        )
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.appendTo(log))
            .start()
    }

    /** `/Applications/QuickNotch.app` (or wherever this copy lives). */
    private fun appBundle(): File? {
        var cursor = packagedPath?.let(::File)
        while (cursor != null && !cursor.name.endsWith(".app", ignoreCase = true)) {
            cursor = cursor.parentFile
        }
        return cursor
    }

    private val MAC_SCRIPT = """
        #!/bin/sh
        # Waits for QuickNotch to quit, then swaps the bundle in place.
        set -u
        pid="${'$'}1"; pkg="${'$'}2"; target="${'$'}3"
        while kill -0 "${'$'}pid" 2>/dev/null; do sleep 1; done
        mnt="${'$'}(mktemp -d "${'$'}{TMPDIR:-/tmp}/quicknotch-update.XXXXXX")"
        cleanup() { hdiutil detach "${'$'}mnt" -quiet >/dev/null 2>&1 || true; rm -rf "${'$'}mnt"; }
        trap cleanup EXIT
        if ! hdiutil attach "${'$'}pkg" -nobrowse -quiet -mountpoint "${'$'}mnt" >/dev/null 2>&1; then
            open "${'$'}pkg"
            exit 1
        fi
        app="${'$'}(find "${'$'}mnt" -maxdepth 1 -name '*.app' -print -quit)"
        if [ -z "${'$'}app" ]; then
            open "${'$'}pkg"
            exit 1
        fi
        # rsync, not cp -R/ditto: those leave an empty bundle behind on this
        # exact layout, which reads as "the update ate my app".
        if ! rsync -a "${'$'}app/Contents/" "${'$'}target/Contents/"; then
            open "${'$'}pkg"
            exit 1
        fi
        open "${'$'}target"
        rm -f "${'$'}pkg"
    """.trimIndent()

    private val WINDOWS_SCRIPT = """
        param([int]${'$'}ProcessId, [string]${'$'}Package, [string]${'$'}Exe)
        # Wait-Process tolerates the app already being gone.
        Wait-Process -Id ${'$'}ProcessId -ErrorAction SilentlyContinue
        if (${'$'}Package -like '*.msi') {
            ${'$'}quiet = Start-Process msiexec.exe -ArgumentList @('/i', "`"${'$'}Package`"", '/qn', '/norestart') -Wait -PassThru
            # 3010 = success, restart required. Anything else gets the normal UI
            # rather than a silent no-op the user cannot see.
            if (${'$'}quiet.ExitCode -ne 0 -and ${'$'}quiet.ExitCode -ne 3010) {
                Start-Process msiexec.exe -ArgumentList @('/i', "`"${'$'}Package`"") -Wait
            }
        } else {
            Start-Process -FilePath ${'$'}Package -Wait
        }
        Start-Process -FilePath ${'$'}Exe
        Remove-Item -LiteralPath ${'$'}Package -Force -ErrorAction SilentlyContinue
    """.trimIndent()
}
