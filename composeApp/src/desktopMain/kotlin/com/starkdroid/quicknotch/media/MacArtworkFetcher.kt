package com.starkdroid.quicknotch.media

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Scripted media contexts get metadata but never artwork, so one
 * entitled perl helper query runs per artless track. A late reply is
 * dropped by content id; a missing helper is a silent no-op.
 */
internal class MacArtworkFetcher(
    private val ioScope: CoroutineScope,
    /** Current poll-loop track id, read at reply time to detect stale fetches. */
    private val currentId: () -> String?,
    private val onArtwork: (contentId: String, base64: String) -> Unit
) {
    @Volatile
    private var driver: File? = null

    @Volatile
    private var helper: File? = null

    /** Content id whose artwork lookup already concluded (found or not). */
    @Volatile
    private var settledId: String? = null

    @Volatile
    private var inFlight = false

    /**
     * Last helper failure and its time; failures back off instead of settling
     * the track.
     */
    @Volatile
    private var failedId: String? = null

    @Volatile
    private var failedAtMs: Long = 0L

    /** Backoff window for [failedId]: long after a timeout, short after a miss. */
    @Volatile
    private var failedRetryMs: Long = FAILED_RETRY_MS

    /**
     * Browsers publish art seconds after metadata, so an artless match
     * retries on a slow bounded budget, then settles.
     */
    @Volatile
    private var emptyId: String? = null

    @Volatile
    private var emptyTries: Int = 0

    @Volatile
    private var emptyLastAtMs: Long = 0L

    fun start() {
        driver = NativeResources.extract("macos/artwork-driver.pl", ".pl")
        helper = NativeResources.extract("macos/artwork-bridge.dylib", ".dylib")
    }

    private val ready: Boolean
        get() = driver != null && helper != null

    /**
     * Requests art for contentId: one run in flight, failures back off, empty
     * answers retry on a budget.
     */
    fun fetch(contentId: String) {
        if (!shouldFetch(contentId)) return
        inFlight = true
        val wanted = contentId
        ioScope.launch {
            val outcome = runHelper()
            inFlight = false
            val nowMs = System.nanoTime() / 1_000_000
            val live = currentId() == wanted
            val missed = live && (!outcome.finished || outcome.trackId != wanted)
            val empty = live && !missed && outcome.base64.isNullOrBlank()
            if (missed) {
                // Timeout/crash, or the daemon was still on another track (it
                // lags behind on switches): back off briefly and let a later
                // poll retry instead of settling. A timeout waits longer than
                // a mere identity miss, which usually resolves within a poll.
                failedId = wanted
                failedAtMs = nowMs
                failedRetryMs = if (!outcome.finished) FAILED_RETRY_MS else MISMATCH_RETRY_MS
            } else if (empty) {
                if (emptyId != wanted) {
                    emptyId = wanted
                    emptyTries = 0
                }
                emptyTries++
                emptyLastAtMs = nowMs
                if (emptyTries >= EMPTY_MAX_TRIES) settledId = wanted
            } else if (live) {
                emptyId = null
                settledId = wanted
                if (!outcome.base64.isNullOrBlank()) onArtwork(wanted, outcome.base64)
            }
            // A track change mid-flight settles nothing: the new track's own
            // polls trigger its fetch once this one lands.
        }
    }

    private fun shouldFetch(contentId: String): Boolean {
        if (!ready || inFlight) return false
        val nowMs = System.nanoTime() / 1_000_000
        val fresh = contentId.isNotBlank() && contentId != settledId
        val cooled = contentId != failedId || nowMs - failedAtMs >= failedRetryMs
        val budgeted = contentId != emptyId ||
            (emptyTries < EMPTY_MAX_TRIES && nowMs - emptyLastAtMs >= EMPTY_RETRY_MS)
        return fresh && cooled && budgeted
    }

    /**
     * One perl run with a 12 s deadline; stdout is drained while waiting
     * because artwork exceeds the pipe buffer and a read after wait deadlocks.
     */
    private fun runHelper(): HelperOutcome {
        val script = driver
        val dylib = helper
        if (script == null || dylib == null) return HelperOutcome(false, null, null)
        return runCatching {
            val process = ProcessBuilder("/usr/bin/perl", script.absolutePath, dylib.absolutePath)
                .redirectErrorStream(false)
                .start()
            val sink = ByteArrayOutputStream()
            val drain = thread(isDaemon = true) {
                runCatching { process.inputStream.copyTo(sink) }
            }
            val finished = process.waitFor(12, TimeUnit.SECONDS)
            drain.join(2_000)
            if (!finished) process.destroyForcibly()
            val line = sink.toString(Charsets.UTF_8)
                .lineSequence()
                .firstOrNull { it.startsWith("{") }
            val reply = line?.let {
                runCatching {
                    MediaJson.parser.decodeFromString(ArtworkReply.serializer(), it)
                }.getOrNull()
            }
            HelperOutcome(finished, reply?.trackId, reply?.artworkBase64?.takeIf { it.length > 16 })
        }.getOrNull() ?: HelperOutcome(false, null, null)
    }
}

/**
 * What one helper run concluded; the track id gates the merge so a lagging
 * daemon cannot settle the wrong cover.
 */
private data class HelperOutcome(val finished: Boolean, val trackId: String?, val base64: String?)

/** How long a timed-out run keeps its track from retrying. */
private const val FAILED_RETRY_MS = 15_000L

/** How long an identity-mismatched run waits: the daemon settles within a poll. */
private const val MISMATCH_RETRY_MS = 3_000L

/**
 * Slow retry cadence for artless-but-matched tracks. The helper already waits
 * ~7s in-process, so this only fires when a cover is pathologically late;
 * kept short enough that one still lands within seconds of publication.
 */
private const val EMPTY_RETRY_MS = 5_000L

/** Empty answers before a track settles artless (backstop, rarely reached). */
private const val EMPTY_MAX_TRIES = 8

/** The helper's one-line answer: whose bytes, and the bytes (or explicit nulls). */
@Serializable
private data class ArtworkReply(
    @SerialName("id") val trackId: String? = null,
    @SerialName("art") val artworkBase64: String? = null
)
