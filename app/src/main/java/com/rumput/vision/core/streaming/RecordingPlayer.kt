package com.rumput.vision.core.streaming

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import com.rumput.vision.domain.model.RecordingSegment
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Call
import javax.inject.Inject
import javax.inject.Singleton

sealed interface PlaybackState {
    data object Idle : PlaybackState
    data object Loading : PlaybackState
    data class Ready(val playing: Boolean, val durationMillis: Long, val buffering: Boolean) : PlaybackState
    data object Ended : PlaybackState
    data class Error(val failure: PlaybackFailure) : PlaybackState
}

enum class PlaybackFailure {
    /** Frigate answered 404: nothing recorded in this period, or it has expired. */
    NOT_FOUND,
    UNAUTHORIZED,
    NETWORK,
    DECODER,
}

/** Plays Frigate VOD (HLS) recordings. Main thread only; release when done. */
interface RecordingPlayer {
    val state: StateFlow<PlaybackState>
    val player: Player?
    fun load(playlistUrl: String)
    fun play()
    fun pause()
    fun seekTo(positionMillis: Long)
    val positionMillis: Long
    fun release()
}

fun interface RecordingPlayerFactory {
    fun create(): RecordingPlayer
}

/** Supplies the call factory for media (authenticated only for the connected server). */
fun interface MediaCallFactoryProvider {
    fun mediaCallFactory(): Call.Factory
}

@Singleton
class Media3RecordingPlayerFactory @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val calls: MediaCallFactoryProvider,
) : RecordingPlayerFactory {
    override fun create(): RecordingPlayer = Media3RecordingPlayer(context, calls.mediaCallFactory())
}

@OptIn(UnstableApi::class)
class Media3RecordingPlayer(context: Context, callFactory: Call.Factory) : RecordingPlayer {
    private val _state = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val sourceFactory = HlsMediaSource.Factory(OkHttpDataSource.Factory(callFactory))
    private var exo: ExoPlayer? = ExoPlayer.Builder(context).build().also { it.addListener(Listener()) }
    override val player: Player? get() = exo

    override fun load(playlistUrl: String) {
        val p = exo ?: return
        _state.value = PlaybackState.Loading
        p.setMediaSource(sourceFactory.createMediaSource(MediaItem.fromUri(playlistUrl)))
        p.prepare()
        p.playWhenReady = true
    }

    override fun play() {
        val p = exo ?: return
        if (p.playbackState == Player.STATE_ENDED) p.seekTo(0)
        p.play()
    }

    override fun pause() { exo?.pause() }

    override fun seekTo(positionMillis: Long) { exo?.seekTo(positionMillis.coerceAtLeast(0)) }

    override val positionMillis: Long get() = exo?.currentPosition ?: 0

    override fun release() {
        exo?.release()
        exo = null
        _state.value = PlaybackState.Idle
    }

    private fun publish() {
        val p = exo ?: return
        _state.value = when (p.playbackState) {
            Player.STATE_ENDED -> PlaybackState.Ended
            Player.STATE_IDLE -> _state.value.takeIf { it is PlaybackState.Error } ?: PlaybackState.Idle
            else -> if (p.duration <= 0 && p.playbackState == Player.STATE_BUFFERING) {
                PlaybackState.Loading
            } else {
                PlaybackState.Ready(playing = p.isPlaying, durationMillis = p.duration.coerceAtLeast(0), buffering = p.playbackState == Player.STATE_BUFFERING)
            }
        }
    }

    private inner class Listener : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish()

        override fun onPlayerError(error: PlaybackException) {
            _state.value = PlaybackState.Error(classify(error))
        }
    }

    companion object {
        fun classify(error: PlaybackException): PlaybackFailure {
            val http = generateSequence<Throwable>(error) { it.cause }.firstNotNullOfOrNull { it as? HttpDataSource.InvalidResponseCodeException }
            return when {
                http?.responseCode == 404 -> PlaybackFailure.NOT_FOUND
                http?.responseCode == 401 || http?.responseCode == 403 -> PlaybackFailure.UNAUTHORIZED
                error.errorCode in PlaybackException.ERROR_CODE_DECODER_INIT_FAILED..PlaybackException.ERROR_CODE_DECODING_RESOURCES_RECLAIMED -> PlaybackFailure.DECODER
                else -> PlaybackFailure.NETWORK
            }
        }
    }
}

/**
 * Maps between playlist position and wall-clock time. Frigate's VOD playlist concatenates only recorded
 * segments, so gaps take no playback time. Verified on 0.18.2: the playlist is trimmed exactly at the
 * requested end, but **not** at the requested start, where it begins at an earlier keyframe/segment start.
 *
 * When the real playlist length is known ([playlistMillis], from the player), the timeline is anchored at
 * the end and walked backwards, which is exact however the start was cut. Without it, both ends are
 * assumed trimmed (an approximation until the player reports its duration).
 */
class RecordingTimeline(
    segments: List<RecordingSegment>,
    rangeStartMillis: Long,
    rangeEndMillis: Long,
    playlistMillis: Long? = null,
) {
    private val pieces: List<RecordingSegment> = run {
        val endTrimmed = segments
            .filter { it.endEpochMillis > rangeStartMillis - MAX_LEAD_MILLIS && it.startEpochMillis < rangeEndMillis }
            .map { RecordingSegment(it.startEpochMillis, minOf(it.endEpochMillis, rangeEndMillis)) }
            .filter { it.endEpochMillis > it.startEpochMillis }
            .sortedBy { it.startEpochMillis }
        if (playlistMillis == null || playlistMillis <= 0) {
            endTrimmed.map { RecordingSegment(maxOf(it.startEpochMillis, rangeStartMillis), it.endEpochMillis) }
                .filter { it.endEpochMillis > it.startEpochMillis }
        } else {
            // Take playlistMillis of recorded time, counting back from the end.
            var remaining = playlistMillis
            endTrimmed.asReversed().mapNotNull { piece ->
                if (remaining <= 0) return@mapNotNull null
                val length = piece.endEpochMillis - piece.startEpochMillis
                val taken = minOf(length, remaining)
                remaining -= taken
                RecordingSegment(piece.endEpochMillis - taken, piece.endEpochMillis)
            }.asReversed()
        }
    }

    val segments: List<RecordingSegment> get() = pieces
    val totalMillis: Long = pieces.sumOf { it.endEpochMillis - it.startEpochMillis }

    fun wallClockAt(positionMillis: Long): Long? {
        var remaining = positionMillis.coerceAtLeast(0)
        for (piece in pieces) {
            val length = piece.endEpochMillis - piece.startEpochMillis
            if (remaining < length) return piece.startEpochMillis + remaining
            remaining -= length
        }
        return pieces.lastOrNull()?.endEpochMillis
    }

    /** Playlist position for [wallClockMillis]; a time inside a gap maps to the next recorded moment. */
    fun positionAt(wallClockMillis: Long): Long {
        var position = 0L
        for (piece in pieces) {
            if (wallClockMillis < piece.startEpochMillis) return position
            if (wallClockMillis < piece.endEpochMillis) return position + (wallClockMillis - piece.startEpochMillis)
            position += piece.endEpochMillis - piece.startEpochMillis
        }
        return position
    }

    private companion object {
        /** Segments are ~10 s; a playlist can start up to one segment before the requested start. */
        const val MAX_LEAD_MILLIS = 60_000L
    }
}
