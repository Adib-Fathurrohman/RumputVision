package com.rumput.vision.domain.repository

/** URLs for camera media on the connected server. All return null when not connected. */
interface CameraMedia {
    /** The camera's current frame (`/api/<camera>/latest.webp`), scaled by the server to [height] pixels. */
    fun latestFrameUrl(cameraName: String, height: Int): String?

    /** go2rtc MSE WebSocket (`/live/mse/api/ws?src=<stream>`), as ws:// or wss:// matching the server URL. */
    fun liveStreamUrl(streamName: String): String?

    fun eventThumbnailUrl(eventId: String): String?

    fun eventSnapshotUrl(eventId: String, height: Int): String?

    /** Frigate VOD (HLS, fMP4) of [camera]'s recordings between two epoch-second times. 404 when none exist. */
    fun recordingPlaylistUrl(camera: String, startEpochSeconds: Long, endEpochSeconds: Long): String?
}
