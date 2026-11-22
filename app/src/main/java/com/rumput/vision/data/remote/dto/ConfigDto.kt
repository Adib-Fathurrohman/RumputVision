package com.rumput.vision.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Subset of `GET /api/config` (Frigate 0.18). The full document also contains camera
 * credentials (go2rtc stream sources); only stream *names* are read from it, and the
 * raw body is never logged or persisted.
 */
@Serializable
data class ConfigDto(
    val cameras: Map<String, CameraConfigDto> = emptyMap(),
    val go2rtc: Go2RtcConfigDto? = null,
    val auth: AuthConfigDto? = null,
)

@Serializable
data class CameraConfigDto(
    val enabled: Boolean = true,
    val ui: CameraUiDto? = null,
    val live: CameraLiveDto? = null,
    val detect: CameraDetectDto? = null,
    val record: EnabledDto? = null,
    val snapshots: EnabledDto? = null,
    val audio: EnabledDto? = null,
)

@Serializable
data class CameraUiDto(val order: Int = 0, val dashboard: Boolean = true)

/** `streams` maps a display label to a go2rtc stream name. */
@Serializable
data class CameraLiveDto(val streams: Map<String, String> = emptyMap())

@Serializable
data class CameraDetectDto(val enabled: Boolean = false, val width: Int? = null, val height: Int? = null)

@Serializable
data class EnabledDto(val enabled: Boolean = false)

@Serializable
data class Go2RtcConfigDto(
    /** Values are camera source URLs with credentials: decoded as opaque JSON and discarded. */
    val streams: Map<String, JsonElement> = emptyMap(),
)

@Serializable
data class AuthConfigDto(
    val enabled: Boolean = false,
    @SerialName("cookie_name") val cookieName: String? = null,
    @SerialName("session_length") val sessionLength: Long? = null,
    @SerialName("refresh_time") val refreshTime: Long? = null,
)
