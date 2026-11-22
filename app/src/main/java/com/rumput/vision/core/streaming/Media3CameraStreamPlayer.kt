package com.rumput.vision.core.streaming

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.util.EventLogger
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.mp4.FragmentedMp4Extractor
import androidx.media3.extractor.text.SubtitleParser
import com.rumput.vision.BuildConfig
import com.rumput.vision.core.logging.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [CameraStreamPlayer] on ExoPlayer: go2rtc MSE (fragmented MP4 over WebSocket) through
 * [MseWebSocketDataSource] and FragmentedMp4Extractor. See docs/STREAMING.md.
 *
 * Latency: buffering follows a [BufferProfile], playback speeds up slightly when well ahead of the live edge,
 * and the data source forces a reconnect if data waits more than a few seconds.
 */
@OptIn(UnstableApi::class)
class Media3CameraStreamPlayer(
    context: Context,
    clients: StreamClientProvider,
    private val codecs: StreamCodecs,
    private val profile: BufferProfile = BufferProfile.LOW_LATENCY,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) : CameraStreamPlayer {

    private val _state = MutableStateFlow<StreamState>(StreamState.Idle)
    override val state: StateFlow<StreamState> = _state.asStateFlow()

    private val _rebufferingFrequently = MutableStateFlow(false)
    override val rebufferingFrequently: StateFlow<Boolean> = _rebufferingFrequently.asStateFlow()
    private val rebufferTimes = ArrayDeque<Long>()

    private val handler = Handler(Looper.getMainLooper())
    private val retryPolicy = RetryPolicy()
    private val dataSourceFactory = MseWebSocketDataSource.Factory(clients) { codecs.forRequest(withAudio = !muted && !audioFailed) }
    private val mediaSourceFactory = ProgressiveMediaSource.Factory(
        dataSourceFactory,
        // Camera streams carry no subtitles.
        ExtractorsFactory { arrayOf(FragmentedMp4Extractor(SubtitleParser.Factory.UNSUPPORTED)) },
    ).setContinueLoadingCheckIntervalBytes(CONTINUE_LOADING_CHECK_BYTES)

    private var exoPlayer: ExoPlayer? = ExoPlayer.Builder(context)
        .setLoadControl(
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(profile.minMs, profile.maxMs, profile.startMs, profile.resumeMs)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build(),
        )
        .setRenderersFactory(DefaultRenderersFactory(context).setEnableDecoderFallback(true))
        // Live CCTV should not take audio focus from other apps; muting is done with the volume.
        .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), false)
        .build()
        .also {
            it.addListener(Listener())
            // Debug builds log Media3's playback events (state changes, dropped frames, errors).
            if (BuildConfig.DEBUG) it.addAnalyticsListener(EventLogger("RumputVisionPlayer"))
        }

    override val player: Player? get() = exoPlayer

    private var source: StreamSource? = null
    private var wantsPlay = false
    private var firstFrameRendered = false

    @Volatile private var muted = true

    /** Audio failed to decode on this stream; keep playing video only until the source changes. */
    @Volatile private var audioFailed = false

    private val retryRunnable = Runnable { startPlayback() }
    private val tick = object : Runnable {
        override fun run() {
            controlLatency()
            handler.postDelayed(this, TICK_MS)
        }
    }

    override fun prepare(source: StreamSource) {
        if (source == this.source) return
        this.source = source
        audioFailed = false
        rebufferTimes.clear()
        _rebufferingFrequently.value = false
        retryPolicy.reset()
        if (wantsPlay) startPlayback()
    }

    override fun play() {
        // Idempotent: callers re-apply state on every update; only a stopped player starts again.
        if (exoPlayer == null || wantsPlay) return
        wantsPlay = true
        startPlayback()
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    override fun stop() {
        wantsPlay = false
        handler.removeCallbacks(retryRunnable)
        handler.removeCallbacks(tick)
        dataSourceFactory.abortAll()
        exoPlayer?.stop()
        if (_state.value !is StreamState.Error) _state.value = StreamState.Idle
    }

    override fun retry() {
        retryPolicy.reset()
        if (wantsPlay) startPlayback()
    }

    override fun setMuted(muted: Boolean) {
        if (muted == this.muted) return
        this.muted = muted
        exoPlayer?.volume = if (muted) 0f else 1f
        // The audio track is only requested while unmuted, so a change needs a new connection.
        if (wantsPlay && !audioFailed) startPlayback()
    }

    override fun release() {
        handler.removeCallbacksAndMessages(null)
        wantsPlay = false
        dataSourceFactory.abortAll()
        exoPlayer?.release()
        exoPlayer = null
        _state.value = StreamState.Released
    }

    private fun startPlayback() {
        val player = exoPlayer ?: return
        handler.removeCallbacks(retryRunnable)
        val src = source ?: return
        // Close any previous connection now rather than when its loader notices.
        dataSourceFactory.abortAll()
        firstFrameRendered = false
        _state.value = StreamState.Connecting
        player.setPlaybackParameters(PlaybackParameters.DEFAULT)
        player.setMediaSource(mediaSourceFactory.createMediaSource(MediaItem.fromUri(src.url)))
        player.prepare()
        player.playWhenReady = true
    }

    /** Speeds up slightly when too much is buffered, so playback stays close to the live edge. */
    private fun controlLatency() {
        val player = exoPlayer ?: return
        if (_state.value !is StreamState.Playing) return
        retryPolicy.onTick(clock())
        val buffered = player.totalBufferedDuration
        val speed = player.playbackParameters.speed
        val target = when {
            buffered > profile.catchUpAboveMs -> CATCH_UP_SPEED
            buffered < profile.normalBelowMs -> 1f
            else -> speed
        }
        if (target != speed) player.setPlaybackParameters(PlaybackParameters(target))
    }

    private fun onError(error: PlaybackException) {
        // Some go2rtc FLAC output is rejected by Android's FLAC decoder (irregular block sizes). Losing
        // audio must not lose video: reconnect without audio and report it.
        if (error is ExoPlaybackException && error.type == ExoPlaybackException.TYPE_RENDERER &&
            exoPlayer?.getRendererType(error.rendererIndex) == C.TRACK_TYPE_AUDIO
        ) {
            AppLogger.w { "Audio failed on ${source?.streamName}; continuing without audio" }
            audioFailed = true
            if (wantsPlay) startPlayback()
            return
        }
        onFailure(classify(error))
    }

    private fun onFailure(failure: StreamFailure) {
        val delay = retryPolicy.nextDelay(failure)
        AppLogger.w { "Stream ${source?.streamName} failed: $failure, retry in $delay ms" }
        exoPlayer?.stop()
        _state.value = StreamState.Error(failure, delay.takeIf { wantsPlay })
        if (wantsPlay && delay != null) handler.postDelayed(retryRunnable, delay)
    }

    private inner class Listener : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    if (firstFrameRendered) recordRebuffer()
                    _state.value = if (firstFrameRendered) StreamState.Buffering else StreamState.Connecting
                }
                Player.STATE_READY -> if (firstFrameRendered) publishPlaying()
                // The server closed the stream: treat it like a dropped connection.
                Player.STATE_ENDED -> if (wantsPlay) onFailure(StreamFailure.NETWORK)
                Player.STATE_IDLE -> Unit
            }
        }

        override fun onRenderedFirstFrame() {
            firstFrameRendered = true
            retryPolicy.onPlaying(clock())
            publishPlaying()
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (firstFrameRendered) publishPlaying()
        }

        override fun onPlayerError(error: PlaybackException) = onError(error)
    }

    private fun recordRebuffer() {
        val now = clock()
        rebufferTimes.addLast(now)
        while (rebufferTimes.isNotEmpty() && now - rebufferTimes.first() > REBUFFER_WINDOW_MS) rebufferTimes.removeFirst()
        if (rebufferTimes.size >= REBUFFERS_TO_FLAG) _rebufferingFrequently.value = true
    }

    private fun publishPlaying() {
        val size = exoPlayer?.videoSize ?: VideoSize.UNKNOWN
        _state.value = StreamState.Playing(size.width, size.height, audioUnavailable = audioFailed && !muted)
    }

    companion object {
        private const val CONTINUE_LOADING_CHECK_BYTES = 32 * 1024
        private const val TICK_MS = 500L
        private const val CATCH_UP_SPEED = 1.15f
        private const val REBUFFER_WINDOW_MS = 30_000L
        private const val REBUFFERS_TO_FLAG = 3

        fun classify(error: PlaybackException): StreamFailure {
            generateSequence<Throwable>(error) { it.cause }.firstNotNullOfOrNull { it as? StreamException }?.let { return it.failure }
            return when (error.errorCode) {
                PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
                PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
                -> StreamFailure.UNSUPPORTED_CODEC
                PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
                PlaybackException.ERROR_CODE_DECODING_FAILED,
                PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
                PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED,
                -> StreamFailure.DECODER
                else -> StreamFailure.NETWORK
            }
        }
    }
}
