package com.starkdroid.quicknotch.datasource

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import java.awt.Desktop
import java.io.File
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean
import com.starkdroid.quicknotch.AppVersion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** What the updater knows right now. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(
        val version: String,
        val asset: UpdateAsset?,
        val releaseUrl: String
    ) : UpdateState

    data class Downloading(val version: String, val progress: Float) : UpdateState
    data class Ready(val version: String, val file: File) : UpdateState
    data class Installing(val version: String) : UpdateState
    data class Failed(val version: String?, val message: String) : UpdateState
}

/**
 * The app-side updater: check GitHub, download the right package, hand it to
 * [AppInstaller], and let the main process quit.
 *
 * Deliberately *not* a silent auto-installer. It downloads quietly, then waits
 * for the user to press "Restart & update": swapping the binary out from under
 * someone who is mid-song or mid-game is not a feature. Everything the user
 * avoids is the manual part — finding the release, downloading the right
 * installer, running it, relaunching.
 */
object AppUpdates {

    private const val START_DELAY_MS = 5_000L
    private const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1_000L
    private const val MIN_CHECK_GAP_MS = 10 * 60 * 1_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jsonClient by lazy {
        HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = 60_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 60_000
            }
        }
    }
    private val checking = AtomicBoolean(false)
    private val downloading = AtomicBoolean(false)
    private val started = AtomicBoolean(false)
    private var lastCheckAt = 0L

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state

    /** Set once an installer is running; the app quits in response. */
    private val _quitting = MutableStateFlow(false)
    val quitting: StateFlow<Boolean> = _quitting

    /** Call once from `main()`. Subsequent calls are no-ops. */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        Changelog.loadCache()
        scope.launch {
            delay(START_DELAY_MS)
            check(force = true)
            while (true) {
                delay(CHECK_INTERVAL_MS)
                check(force = true)
            }
        }
    }

    /**
     * Ask GitHub for the latest release. [force] bypasses the debounce; the
     * changelog panel uses the debounced form so opening it repeatedly does
     * not hammer the API.
     */
    @Suppress("TooGenericExceptionCaught")
    fun check(force: Boolean = false) {
        val mayCheck = !busy() &&
            (force || System.currentTimeMillis() - lastCheckAt >= MIN_CHECK_GAP_MS) &&
            checking.compareAndSet(false, true)
        if (mayCheck) {
            scope.launch {
                try {
                    refresh()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    AppLog.line("update check failed: ${failure.message}")
                } finally {
                    lastCheckAt = System.currentTimeMillis()
                    checking.set(false)
                }
            }
        }
    }

    /** Start the background download for the available release, if any. */
    fun download() {
        val available = _state.value as? UpdateState.Available
        val asset = available?.asset
        if (available == null || asset == null || !AppInstaller.canSelfUpdate) return
        if (!downloading.compareAndSet(false, true)) return
        scope.launch {
            try {
                runDownload(available.version, asset)
            } finally {
                downloading.set(false)
            }
        }
    }

    /** Install the downloaded package and let `main()` quit the process. */
    fun install() {
        val ready = _state.value as? UpdateState.Ready ?: return
        _state.value = UpdateState.Installing(ready.version)
        if (AppInstaller.launch(ready.file)) {
            _quitting.value = true
        } else {
            _state.value = UpdateState.Failed(ready.version, "Could not start the installer")
        }
    }

    fun openReleasePage() {
        val url = (_state.value as? UpdateState.Available)?.releaseUrl
            ?: ReleaseApi.RELEASES_PAGE
        runCatching { Desktop.getDesktop().browse(URI(url)) }
            .onFailure { AppLog.line("update: could not open browser: ${it.message}") }
    }

    private suspend fun refresh() {
        _state.value = UpdateState.Checking
        // The release carries the exact changelog asset URL, which beats the
        // `/releases/latest/download/...` guess while the repository still has
        // an old tag that sorts higher than 2.x.
        val release = ReleaseApi.latest()
        Changelog.refresh(release?.changelogUrl)

        val version = release?.version ?: Changelog.feed.value.latest
        if (version == null || !AppVersions.isNewer(version, AppVersion.VERSION)) {
            _state.value = UpdateState.UpToDate
            return
        }

        val asset = release?.let { ReleaseApi.assetFor(it) }
        val checksum = if (release != null && asset != null) {
            ReleaseApi.checksumFor(release, asset.name)
        } else {
            null
        }
        _state.value = UpdateState.Available(
            version = version,
            asset = asset?.copy(sha256 = checksum),
            releaseUrl = release?.pageUrl ?: ReleaseApi.RELEASES_PAGE
        )
        // Fetch the package in the background: the only decision left to the
        // user is when to restart. `download()` no-ops when this build cannot
        // replace itself (a Gradle run) or the release has nothing for this
        // platform, in which case the banner offers the release page instead.
        download()
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun runDownload(version: String, asset: UpdateAsset) {
        val directory = File(AppPaths.configDir, "updates").apply { mkdirs() }
        // One package at a time: a stale download from an older version would
        // only waste disk and confuse a later install.
        directory.listFiles()?.forEach { it.delete() }
        val target = File(directory, asset.name)

        try {
            _state.value = UpdateState.Downloading(version, 0f)
            val response = jsonClient.get(asset.url) {
                header("User-Agent", ReleaseApi.userAgent())
            }
            if (!response.status.isSuccess()) error("HTTP ${response.status.value}")

            val total = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
                ?.takeIf { it > 0 } ?: asset.size
            streamToFile(response.bodyAsChannel(), target, total) { progress ->
                _state.value = UpdateState.Downloading(version, progress)
            }
            FileDigest.verify(target, asset.sha256)
            _state.value = UpdateState.Ready(version, target)
            AppLog.line("update: downloaded ${target.name} for $version")
        } catch (cancelled: CancellationException) {
            target.delete()
            throw cancelled
        } catch (failure: Exception) {
            target.delete()
            _state.value = UpdateState.Failed(version, failure.message ?: "Download failed")
            AppLog.line("update: download failed: ${failure.message}")
        }
    }

    private fun busy(): Boolean = when (_state.value) {
        is UpdateState.Downloading,
        is UpdateState.Ready,
        is UpdateState.Installing -> true
        else -> false
    }
}
