package com.rumput.vision.core.streaming

import android.media.MediaCodecList
import android.media.MediaFormat

/**
 * Codec lists sent to go2rtc in the MSE request. go2rtc picks from them; for cameras with G.711 (PCMA/PCMU)
 * audio it transcodes to FLAC, which is the only audio it offers over MSE for them (verified).
 * Without an audio codec in the list, go2rtc sends video only: used while muted, saving bandwidth and
 * server CPU. H.265 is offered only if the device can decode it.
 */
data class StreamCodecs(val video: String, val audio: String) {
    fun forRequest(withAudio: Boolean): String = if (withAudio) "$video,$audio" else video

    companion object {
        private const val AVC = "avc1.640029,avc1.64002A,avc1.640033"
        private const val HEVC = "hvc1.1.6.L153.B0"
        private const val AUDIO = "mp4a.40.2,mp4a.40.5,flac,opus"

        fun forDevice(): StreamCodecs = build(supportsHevc = hasDecoder(MediaFormat.MIMETYPE_VIDEO_HEVC))

        fun build(supportsHevc: Boolean): StreamCodecs =
            StreamCodecs(video = listOfNotNull(AVC, HEVC.takeIf { supportsHevc }).joinToString(","), audio = AUDIO)

        private fun hasDecoder(mime: String): Boolean = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
            !info.isEncoder && info.supportedTypes.any { it.equals(mime, ignoreCase = true) }
        }
    }
}
