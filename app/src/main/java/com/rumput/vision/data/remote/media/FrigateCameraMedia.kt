package com.rumput.vision.data.remote.media

import com.rumput.vision.data.remote.FrigateSession
import com.rumput.vision.domain.repository.CameraMedia
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
}
