package com.starkdroid.quicknotch.datasource

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import com.starkdroid.quicknotch.AppVersion
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One downloadable file attached to a release. */
data class UpdateAsset(
    val name: String,
    val url: String,
    val size: Long,
    val sha256: String? = null
)

/** A release the updater is allowed to act on. */
data class LatestRelease(
    val version: String,
    val pageUrl: String,
    val assets: List<UpdateAsset>,
    /** The release's own `changelog.json`, so the feed URL never has to guess. */
    val changelogUrl: String? = null
)

/**
 * Everything that talks to the GitHub releases API.
 *
 * Kept separate from [AppUpdates] so the decision logic ("is this newer?",
 * "download/install") never doubles as an HTTP client, and so every request
 * has one place to carry the User-Agent GitHub requires.
 *
 * A release only counts as *managed* when it carries the cumulative
 * `changelog.json` asset. That is the marker the release workflow adds from
 * 2.0.0 onward; it also keeps an older, pre-changelog release from looking like
 * an update simply because its tag happens to sort higher.
 */
object ReleaseApi {

    const val OWNER = "StarkDroid"
    const val REPO = "QuickNotch"
    const val RELEASES_PAGE = ProjectLinks.RELEASES

    private const val RELEASES_URL = "https://api.github.com/repos/$OWNER/$REPO/releases?per_page=20"
    private const val CHECKSUMS = "SHA256SUMS.txt"
    private const val CHANGELOG = "changelog.json"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client by lazy {
        HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = 20_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 20_000
            }
        }
    }

    suspend fun latest(): LatestRelease? = runCatching {
        val response = client.get(RELEASES_URL) {
            header("Accept", "application/vnd.github+json")
            header("User-Agent", userAgent())
        }
        if (!response.status.isSuccess()) return@runCatching null

        // The list, not `/releases/latest`: this repository still carries a
        // pre-changelog v3.1.2 whose tag sorts above every 2.x release, and
        // `/latest` would keep returning it. The first *managed* release is
        // the real newest one.
        val payload = json.decodeFromString<List<GitHubRelease>>(response.bodyAsText())
        payload.firstNotNullOfOrNull { it.toLatest() }
    }.getOrNull()

    private fun GitHubRelease.toLatest(): LatestRelease? {
        val mapped = assets.map { UpdateAsset(it.name, it.downloadUrl, it.size) }
        // The changelog asset is the "this release is managed by the workflow"
        // marker; see the class comment.
        val changelog = mapped.firstOrNull { it.name == CHANGELOG }
        return if (draft || prerelease || changelog == null) {
            null
        } else {
            LatestRelease(
                version = tagName.removePrefix("v"),
                pageUrl = htmlUrl.ifBlank { RELEASES_PAGE },
                assets = mapped,
                changelogUrl = changelog.url
            )
        }
    }

    /** The package for this host, or null when the release has no build for it. */
    fun assetFor(release: LatestRelease): UpdateAsset? = if (HostPlatform.isMac) {
        macAsset(release)
    } else {
        release.assets.firstOrNull { it.name.endsWith(".msi", ignoreCase = true) }
            ?: release.assets.firstOrNull { it.name.endsWith(".exe", ignoreCase = true) }
    }

    /**
     * Two DMGs ship per release (`-arm64.dmg` / `-x64.dmg`). Pick the one this
     * machine can run; an unlabelled DMG is treated as universal.
     */
    private fun macAsset(release: LatestRelease): UpdateAsset? {
        val images = release.assets.filter { it.name.endsWith(".dmg", ignoreCase = true) }
        if (images.isEmpty()) return null
        val arch = System.getProperty("os.arch").orEmpty().lowercase()
        val tokens = if (arch.contains("aarch64") || arch.contains("arm64")) {
            listOf("arm64", "aarch64", "apple")
        } else {
            listOf("x64", "x86_64", "intel")
        }
        return images.firstOrNull { image -> tokens.any { image.name.contains(it, ignoreCase = true) } }
            ?: images.firstOrNull { image ->
                listOf("arm64", "x64", "x86_64").none { image.name.contains(it, ignoreCase = true) }
            }
            ?: images.first()
    }

    /**
     * Read the SHA-256 for [assetName] from the release's checksum file. A
     * missing file is not fatal — the download still installs — but a present
     * file is always checked, because a corrupted installer is worse than no
     * installer.
     */
    suspend fun checksumFor(release: LatestRelease, assetName: String): String? = runCatching {
        val checksums = release.assets.firstOrNull { it.name == CHECKSUMS } ?: return@runCatching null
        val response = client.get(checksums.url) {
            header("User-Agent", userAgent())
        }
        if (!response.status.isSuccess()) return@runCatching null
        response.bodyAsText().lineSequence()
            .map { it.trim().split(Regex("\\s+")) }
            .firstOrNull { it.size >= 2 && it.last() == assetName }
            ?.first()
            ?.lowercase()
    }.getOrNull()

    fun userAgent(): String = "QuickNotch/${AppVersion.VERSION} (${System.getProperty("os.name")})"

    @Serializable
    private data class GitHubRelease(
        @SerialName("tag_name") val tagName: String = "",
        @SerialName("html_url") val htmlUrl: String = "",
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        val assets: List<GitHubAsset> = emptyList()
    )

    @Serializable
    private data class GitHubAsset(
        val name: String = "",
        @SerialName("browser_download_url") val downloadUrl: String = "",
        val size: Long = 0
    )
}

/**
 * Semver comparison for the versions the workflow can produce (x.y.z, with an
 * optional leading `v`). Deliberately tolerant: a malformed component counts as
 * zero rather than throwing, because the updater must never take the app down
 * over a tag it does not understand.
 */
object AppVersions {

    fun isNewer(candidate: String, current: String): Boolean =
        compare(candidate, current) > 0

    private fun compare(a: String, b: String): Int {
        val left = parts(a)
        val right = parts(b)
        for (index in 0 until maxOf(left.size, right.size)) {
            val diff = (left.getOrNull(index) ?: 0) - (right.getOrNull(index) ?: 0)
            if (diff != 0) return diff
        }
        return 0
    }

    private fun parts(version: String): List<Int> =
        version.trim()
            .removePrefix("v")
            .substringBefore('-')
            .substringBefore('+')
            .split('.')
            .map { it.toIntOrNull() ?: 0 }
}
