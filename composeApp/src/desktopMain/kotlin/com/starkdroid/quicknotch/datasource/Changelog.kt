package com.starkdroid.quicknotch.datasource

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import java.io.File
import com.starkdroid.quicknotch.AppVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The changelog as published by the release workflow.
 *
 * The feed is deliberately *not* bundled with the app: a build ships without
 * any release notes at all and reads them from GitHub, so the history a user
 * sees is the real published history rather than whatever was frozen into the
 * installer months ago. The URL is the `changelog.json` asset on the newest
 * managed release (falling back to the `releases/latest` download path), which
 * the workflow rewrites with the full cumulative list on every release.
 *
 * [releases] is newest-first. The UI keeps only entries at or below the running
 * version — a newer entry is an *update*, not something this build should
 * pretend to document (see `AppUpdates`).
 */
@Serializable
data class ChangelogFeed(
    val latest: String? = null,
    val releases: List<ChangelogRelease> = emptyList()
) {
    companion object {
        val Empty = ChangelogFeed()
    }
}

@Serializable
data class ChangelogRelease(
    val version: String = "",
    val date: String = "",
    val title: String = "",
    val highlights: List<String> = emptyList(),
    val changes: List<ChangelogChange> = emptyList()
)

@Serializable
data class ChangelogChange(
    val kind: String = "improved",
    val text: String = ""
)

/** What the changelog panel is currently showing. */
sealed interface ChangelogStatus {
    data object Idle : ChangelogStatus
    data object Loading : ChangelogStatus
    data object Ready : ChangelogStatus
    data object Offline : ChangelogStatus
}

/**
 * Reads the published changelog and keeps a last-known-good copy on disk.
 *
 * Cached-on-disk matters more than it looks: the first thing the panel paints
 * after a cold start should be the history the user saw last time, not a
 * spinner. A failed refresh keeps that copy (and reports [ChangelogStatus.Offline]
 * only when there is nothing cached at all), the same fail-soft rule the
 * weather and system widgets follow.
 */
object Changelog {

    private const val FEED_URL =
        "https://github.com/StarkDroid/QuickNotch/releases/latest/download/changelog.json"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val cacheFile: File get() = File(AppPaths.configDir, "changelog.json")
    private val client by lazy {
        HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 8_000
                socketTimeoutMillis = 15_000
            }
        }
    }

    private val _feed = MutableStateFlow(ChangelogFeed.Empty)
    val feed: StateFlow<ChangelogFeed> = _feed

    private val _status = MutableStateFlow<ChangelogStatus>(ChangelogStatus.Idle)
    val status: StateFlow<ChangelogStatus> = _status

    /** The cached copy, if there is one. Cheap enough to call on app start. */
    fun loadCache() {
        val cached = runCatching {
            if (!cacheFile.isFile) return@runCatching null
            json.decodeFromString<ChangelogFeed>(cacheFile.readText())
        }.getOrNull() ?: return
        if (cached.releases.isNotEmpty()) {
            _feed.value = cached
            _status.value = ChangelogStatus.Ready
        }
    }

    /**
     * Fetch the feed. A network failure is not an error the user must act on:
     * keep the cached history and only surface [ChangelogStatus.Offline] when
     * the panel would otherwise be empty.
     */
    suspend fun refresh(feedUrl: String? = null): Boolean = withContext(Dispatchers.IO) {
        _status.value = ChangelogStatus.Loading
        val result = runCatching {
            val response = client.get(feedUrl ?: FEED_URL) {
                header("Accept", "application/json")
                header("User-Agent", "QuickNotch/${AppVersion.VERSION}")
            }
            if (!response.status.isSuccess()) error("HTTP ${response.status.value}")
            json.decodeFromString<ChangelogFeed>(response.bodyAsText())
        }

        result.onSuccess { fresh ->
            if (fresh.releases.isNotEmpty()) {
                _feed.value = fresh
                _status.value = ChangelogStatus.Ready
                runCatching { cacheFile.writeText(json.encodeToString(ChangelogFeed.serializer(), fresh)) }
            } else {
                _status.value = if (_feed.value.releases.isEmpty()) {
                    ChangelogStatus.Offline
                } else {
                    ChangelogStatus.Ready
                }
            }
        }.onFailure { failure ->
            AppLog.line("changelog refresh failed: ${failure.message}")
            _status.value = if (_feed.value.releases.isEmpty()) {
                ChangelogStatus.Offline
            } else {
                ChangelogStatus.Ready
            }
        }
        result.isSuccess
    }
}
