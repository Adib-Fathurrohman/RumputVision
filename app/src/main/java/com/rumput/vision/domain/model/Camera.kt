package com.rumput.vision.domain.model

/** Camera metadata from Frigate's config. The list is dynamic: no fixed camera count is assumed. */
data class Camera(
    val name: String,
    val displayName: String,
    val enabled: Boolean,
    val order: Int,
    val showOnDashboard: Boolean,
    /** go2rtc streams offered for live view (`live.streams`), in config order. */
    val liveStreams: List<CameraStream>,
    val detectWidth: Int?,
    val detectHeight: Int?,
    val detectEnabled: Boolean,
    val recordingEnabled: Boolean,
    val snapshotsEnabled: Boolean,
    val audioEnabled: Boolean,
)

data class CameraStream(val label: String, val streamName: String)

data class CameraStats(
    val cameraFps: Double,
    val detectionFps: Double,
    val detectionEnabled: Boolean,
    val connectionQuality: String?,
    val reconnectsLastHour: Int?,
)

data class CameraWithStatus(
    val camera: Camera,
    /** [CameraStatus.UNKNOWN] whenever the data is cached rather than freshly fetched. */
    val status: CameraStatus,
    val stats: CameraStats?,
    val lastSeenOnlineEpochMillis: Long?,
    /** Metadata came from the local cache, not from a successful fetch in this connection. */
    val isCached: Boolean,
)

data class ServerStatus(
    val version: String?,
    val latestVersion: String?,
    val uptimeSeconds: Long?,
    val detectors: List<DetectorStatus>,
    val recordingStorage: StorageUsage?,
    val fetchedAtEpochMillis: Long,
) {
    val updateAvailable: Boolean
        get() = version != null && latestVersion != null && !version.startsWith(latestVersion) && latestVersion != "disabled"
}

data class DetectorStatus(val name: String, val inferenceSpeedMs: Double?)

/** Sizes in MB, as reported by Frigate. */
data class StorageUsage(val usedMb: Double, val totalMb: Double) {
    val fraction: Float get() = if (totalMb > 0) (usedMb / totalMb).toFloat().coerceIn(0f, 1f) else 0f
}
