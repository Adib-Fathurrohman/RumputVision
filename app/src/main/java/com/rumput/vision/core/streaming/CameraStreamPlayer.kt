package com.rumput.vision.core.streaming

import androidx.media3.common.Player
import kotlinx.coroutines.flow.StateFlow

/** What to play: a go2rtc stream at a WebSocket URL. */
data class StreamSource(val cameraName: String, val streamName: String, val url: String)

sealed interface StreamState {
    data object Idle : StreamState

    /** Opening the WebSocket and waiting for the first frame. */
    data object Connecting : StreamState

    /** Playing, but waiting for more data. */
    data object Buffering : StreamState

    /** [audioUnavailable]: audio was requested but could not be decoded, so the stream continues without it. */
    data class Playing(val videoWidth: Int, val videoHeight: Int, val audioUnavailable: Boolean = false) : StreamState

    /**
     * Playback failed. When [retryInMillis] is set, a retry is already scheduled; otherwise the user must
     * retry (or the failure is not retryable).
     */
    data class Error(val failure: StreamFailure, val retryInMillis: Long?) : StreamState

    data object Released : StreamState
}

/**
 * One live camera stream. Created by [CameraStreamPlayerFactory], never inside a composable, so
 * recomposition cannot create players. All methods must be called on the main thread.
 *
 * Lifecycle: [prepare] → [play] / [stop] (on screen start/stop) → [release] (when the screen is left).
 */
interface CameraStreamPlayer {
    val state: StateFlow<StreamState>

    /**
     * True when playback of the current source has stalled repeatedly (≥ 3 times in 30 s): the network
     * cannot keep up, and a lower-bitrate stream would play better. Reset when the source changes.
     */
    val rebufferingFrequently: StateFlow<Boolean>

    /** The Media3 player to attach to a surface, or null after [release]. */
    val player: Player?

    /** Selects what to play. Takes effect immediately if playing. */
    fun prepare(source: StreamSource)

    fun play()

    /** Closes the network connection (no background streaming). [play] reconnects at the live edge. */
    fun stop()

    /** Manual retry after an error; resets the backoff. */
    fun retry()

    /**
     * Muted streams are requested without audio (video only). Unmuting reconnects with audio, which takes
     * until the next keyframe.
     */
    fun setMuted(muted: Boolean)

    /** Idempotent. */
    fun release()
}

/**
 * Buffering trade-off between latency and smoothness. Measured on a phone on a congested 2.4 GHz link:
 * data arrived at full average rate but in bursts up to ~3 s late, so a deeper buffer rides out the gaps.
 */
enum class BufferProfile(
    val minMs: Int,
    val maxMs: Int,
    val startMs: Int,
    val resumeMs: Int,
    val catchUpAboveMs: Long,
    val normalBelowMs: Long,
) {
    /** Single-camera live view: start fast, stay close to live; a 300 ms resume threshold re-stalled at once. */
    LOW_LATENCY(minMs = 1_000, maxMs = 2_500, startMs = 200, resumeMs = 1_000, catchUpAboveMs = 2_000, normalBelowMs = 800),

    /** Grid overview: a few seconds more latency in exchange for fewer stalls. */
    SMOOTH(minMs = 2_500, maxMs = 5_000, startMs = 500, resumeMs = 2_000, catchUpAboveMs = 4_500, normalBelowMs = 2_500),
}
