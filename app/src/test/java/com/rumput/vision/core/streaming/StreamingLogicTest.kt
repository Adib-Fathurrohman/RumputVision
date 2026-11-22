package com.rumput.vision.core.streaming

import androidx.media3.common.PlaybackException
import com.rumput.vision.data.remote.dto.CameraConfigDto
import com.rumput.vision.data.remote.dto.FfmpegDto
import com.rumput.vision.data.remote.dto.FfmpegInputDto
import com.rumput.vision.data.remote.dto.mainRestreamName
import com.rumput.vision.data.remote.dto.restreamName
import com.rumput.vision.domain.model.StreamQuality
import com.rumput.vision.domain.model.hasQualityChoice
import com.rumput.vision.domain.model.streamFor
import com.rumput.vision.testing.cameraWithStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class StreamingLogicTest {
    @Test
    fun `retry backoff doubles to a cap, resets after stable playback, and never retries fatal failures`() {
        val policy = RetryPolicy(initialDelayMillis = 1_000, maxDelayMillis = 8_000, stableAfterMillis = 15_000)
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 8_000L), List(5) { policy.nextDelay(StreamFailure.NETWORK) })
        assertEquals(0L, policy.nextDelay(StreamFailure.FELL_BEHIND))
        assertNull(policy.nextDelay(StreamFailure.UNAUTHORIZED))
        assertNull(policy.nextDelay(StreamFailure.UNSUPPORTED_CODEC))

        policy.onPlaying(nowMillis = 0)
        policy.onTick(nowMillis = 14_000)
        assertEquals(8_000L, policy.nextDelay(StreamFailure.NETWORK))
        policy.onPlaying(nowMillis = 20_000)
        policy.onTick(nowMillis = 35_000)
        assertEquals(1_000L, policy.nextDelay(StreamFailure.NETWORK))
    }

    @Test
    fun `codec offer includes audio only when wanted, and H265 only when decodable`() {
        val withHevc = StreamCodecs.build(supportsHevc = true)
        assertTrue(withHevc.forRequest(withAudio = false).contains("hvc1"))
        assertFalse(withHevc.forRequest(withAudio = false).contains("flac"))
        assertTrue(withHevc.forRequest(withAudio = true).contains("flac"))
        assertFalse(StreamCodecs.build(supportsHevc = false).forRequest(withAudio = true).contains("hvc1"))
    }

    @Test
    fun `playback errors are classified from the cause chain first`() {
        val streamCause = PlaybackException("x", StreamException(StreamFailure.UNAUTHORIZED, "401"), PlaybackException.ERROR_CODE_IO_UNSPECIFIED)
        assertEquals(StreamFailure.UNAUTHORIZED, Media3CameraStreamPlayer.classify(streamCause))
        assertEquals(
            StreamFailure.UNSUPPORTED_CODEC,
            Media3CameraStreamPlayer.classify(PlaybackException("x", null, PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED)),
        )
        assertEquals(
            StreamFailure.DECODER,
            Media3CameraStreamPlayer.classify(PlaybackException("x", null, PlaybackException.ERROR_CODE_DECODING_FAILED)),
        )
        assertEquals(
            StreamFailure.NETWORK,
            Media3CameraStreamPlayer.classify(PlaybackException("x", IOException(), PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)),
        )
    }

    @Test
    fun `only Frigate's own restream paths yield stream names`() {
        assertEquals("garasi_main", FfmpegInputDto("rtsp://127.0.0.1:8554/garasi_main", listOf("record")).restreamName())
        assertEquals("x", FfmpegInputDto("rtsp://localhost:8554/x?video=copy", listOf("record")).restreamName())
        assertNull(FfmpegInputDto("rtsp://admin:pw@192.168.100.213:554/1/1", listOf("record")).restreamName())
        assertNull(FfmpegInputDto("rtsp://127.0.0.1:8554/../etc", listOf("record")).restreamName())

        val camera = CameraConfigDto(
            ffmpeg = FfmpegDto(
                listOf(
                    FfmpegInputDto("rtsp://127.0.0.1:8554/garasi_main", listOf("record")),
                    FfmpegInputDto("rtsp://127.0.0.1:8554/garasi_sub", listOf("detect")),
                ),
            ),
        )
        assertEquals("garasi_main", camera.mainRestreamName())
        assertTrue(FfmpegInputDto("rtsp://admin:secret@cam/1", emptyList()).toString().contains("redacted"))
    }

    @Test
    fun `quality maps to sub and main streams with fallbacks`() {
        val camera = cameraWithStatus("garasi").camera
        assertEquals("garasi_sub", camera.streamFor(StreamQuality.SUB))
        assertEquals("garasi_main", camera.streamFor(StreamQuality.MAIN))
        assertTrue(camera.hasQualityChoice)

        val subOnly = camera.copy(mainStreamName = null)
        assertEquals("garasi_sub", subOnly.streamFor(StreamQuality.MAIN))
        assertFalse(subOnly.hasQualityChoice)
        assertEquals("garasi_main", camera.copy(liveStreams = emptyList()).streamFor(StreamQuality.SUB))
    }
}
