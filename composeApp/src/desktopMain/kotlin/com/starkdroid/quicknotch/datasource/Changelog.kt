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
 * The changelog published by the release workflow, read from GitHub rather than bundled so
 * the history is the real published one. [releases] is newest-first; the UI hides newer versions.
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

sealed interface ChangelogStatus {
    data object Idle : ChangelogStatus
    data object Loading : ChangelogStatus
    data object Ready : ChangelogStatus
    data object Offline : ChangelogStatus
}

/**
 * Reads the published changelog and keeps a last-known-good copy on disk.
 * A failed refresh keeps the cache and reports Offline only when nothing is cached.
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
     * Fetch the feed; a failure keeps the cached history and only surfaces Offline when nothing is cached.
     */
    suspend fun refresh(feedUrl: String? = null): Boolean = withContext(Dispatchers.IO) {
        _status.value = ChangelogStatus.Loading
        // Name the URL in the failure log: a bare "HTTP 404" cannot tell a rate limit from a missing feed.
        val url = feedUrl ?: FEED_URL
        val result = runCatching {
            val response = client.get(url) {
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
            AppLog.line("changelog refresh failed for $url: ${failure.message}")
            _status.value = if (_feed.value.releases.isEmpty()) {
                ChangelogStatus.Offline
            } else {
                ChangelogStatus.Ready
            }
        }
        result.isSuccess
    }
}
