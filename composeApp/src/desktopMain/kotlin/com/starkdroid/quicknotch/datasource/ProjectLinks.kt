package com.starkdroid.quicknotch.datasource

import java.awt.Desktop
import java.net.URI

/**
 * The project's public addresses, in one place so the tray, the changelog and
 * the update banner cannot drift apart.
 */
object ProjectLinks {

    const val REPOSITORY = "https://github.com/StarkDroid/QuickNotch"
    const val RELEASES = "$REPOSITORY/releases/latest"
    const val KOFI = "https://ko-fi.com/trishiraj"

    /** Opens [url] in the system browser; a headless host just stays quiet. */
    fun open(url: String) {
        runCatching { Desktop.getDesktop().browse(URI(url)) }
            .onFailure { AppLog.line("links: could not open $url: ${it.message}") }
    }
}
