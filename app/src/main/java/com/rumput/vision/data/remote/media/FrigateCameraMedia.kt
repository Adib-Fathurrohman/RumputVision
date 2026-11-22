package com.rumput.vision.data.remote.media

import com.rumput.vision.data.remote.FrigateSession
import com.rumput.vision.domain.repository.CameraMedia
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FrigateCameraMedia @Inject constructor(
    private val session: FrigateSession,
) : CameraMedia {
    override fun latestFrameUrl(cameraName: String, height: Int): String? {
        val base = session.connectedBaseUrl ?: return null
        return "${base.value}/".toHttpUrl().newBuilder()
            .addPathSegment("api")
            .addPathSegment(cameraName) // encoded, so unusual camera names cannot alter the path
            .addPathSegment("latest.webp")
            .addQueryParameter("height", height.toString())
            .build()
            .toString()
    }

    override fun liveStreamUrl(streamName: String): String? {
        val base = session.connectedBaseUrl ?: return null
        val http = "${base.value}/".toHttpUrl().newBuilder()
            .addPathSegments("live/mse/api/ws")
            .addQueryParameter("src", streamName)
            .build()
            .toString()
        return if (base.isHttps) "wss://" + http.removePrefix("https://") else "ws://" + http.removePrefix("http://")
    }

    override fun eventThumbnailUrl(eventId: String): String? =
        url { addPathSegments("api/events").addPathSegment(eventId).addPathSegment("thumbnail.webp") }

    override fun eventSnapshotUrl(eventId: String, height: Int): String? = url {
        addPathSegments("api/events").addPathSegment(eventId).addPathSegment("snapshot.jpg")
            .addQueryParameter("height", height.toString())
    }

    override fun recordingPlaylistUrl(camera: String, startEpochSeconds: Long, endEpochSeconds: Long): String? = url {
        addPathSegment("vod").addPathSegment(camera)
            .addPathSegment("start").addPathSegment(startEpochSeconds.toString())
            .addPathSegment("end").addPathSegment(endEpochSeconds.toString())
            .addPathSegment("index.m3u8")
    }

    private fun url(build: HttpUrl.Builder.() -> Unit): String? {
        val base = session.connectedBaseUrl ?: return null
        return "${base.value}/".toHttpUrl().newBuilder().apply(build).build().toString()
    }
}
