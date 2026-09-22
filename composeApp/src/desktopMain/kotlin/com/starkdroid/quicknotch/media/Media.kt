package com.starkdroid.quicknotch.media

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap

/** State of the host media session, normalized across platforms. */
enum class PlaybackState { PLAYING, PAUSED, STOPPED, NONE }

/**
 * A single point-in-time sample of the system's active media session — the
 * "now playing" surface the OS maintains for whichever app currently owns the
 * media keys (Music/Spotify/browsers on macOS via MediaRemote; the SMTC
 * session on Windows).
 *
 * [sampledAt] is a monotonic millisecond stamp taken when the sample was made;
 * [displayedPositionMs] extrapolates forward from it so the progress and the
 * vinyl can advance at frame rate without polling the controller.
 */
@Immutable
data class MediaSession(
    val source: String,
    val title: String,
    val artist: String,
    val album: String,
    val state: PlaybackState,
    /** Playback position at [sampledAt], in milliseconds. */
    val positionMs: Long,
    val durationMs: Long,
    val sampledAt: Long,
    val artwork: ImageBitmap?,
    val contentId: String,
    /** Whether the backend can seek to an arbitrary position (Windows SMTC). */
    val canSeek: Boolean = false
) {
    val isActive: Boolean
        get() = state != PlaybackState.NONE && title.isNotBlank()

    fun displayedPositionMs(nowMs: Long): Long =
        if (state == PlaybackState.PLAYING) {
            (positionMs + (nowMs - sampledAt).coerceAtLeast(0L)).let { p ->
                if (durationMs > 0) p.coerceAtMost(durationMs) else p
            }
        } else {
            positionMs
        }

    fun progressFraction(nowMs: Long): Float =
        if (durationMs <= 0L) 0f
        else (displayedPositionMs(nowMs).toFloat() / durationMs).coerceIn(0f, 1f)
}

/**
 * The wire format both platform bridges emit (JSON per line / per poll). It is
 * intentionally identical across the macOS helper and the Windows PowerShell
 * script so a single decoder feeds both controllers.
 */
@kotlinx.serialization.Serializable
internal data class MediaSnapshot(
    @kotlinx.serialization.SerialName("state") val state: String = "none",
    @kotlinx.serialization.SerialName("src") val source: String = "",
    @kotlinx.serialization.SerialName("title") val title: String = "",
    @kotlinx.serialization.SerialName("artist") val artist: String = "",
    @kotlinx.serialization.SerialName("album") val album: String = "",
    @kotlinx.serialization.SerialName("pos") val positionMs: Long = 0,
    @kotlinx.serialization.SerialName("dur") val durationMs: Long = 0,
    @kotlinx.serialization.SerialName("art") val artworkBase64: String? = null,
    @kotlinx.serialization.SerialName("id") val contentId: String = ""
)
