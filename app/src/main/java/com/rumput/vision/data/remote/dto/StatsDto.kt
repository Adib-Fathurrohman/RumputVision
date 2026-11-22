package com.rumput.vision.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Subset of `GET /api/stats` (Frigate 0.18). */
@Serializable
data class StatsDto(
    val cameras: Map<String, CameraStatsDto> = emptyMap(),
    val detectors: Map<String, DetectorStatsDto> = emptyMap(),
    val service: ServiceStatsDto? = null,
)

@Serializable
data class CameraStatsDto(
    @SerialName("camera_fps") val cameraFps: Double = 0.0,
    @SerialName("process_fps") val processFps: Double = 0.0,
    @SerialName("detection_fps") val detectionFps: Double = 0.0,
    @SerialName("detection_enabled") val detectionEnabled: Boolean = false,
    @SerialName("connection_quality") val connectionQuality: String? = null,
    @SerialName("expected_fps") val expectedFps: Double? = null,
    @SerialName("reconnects_last_hour") val reconnectsLastHour: Int? = null,
    @SerialName("stalls_last_hour") val stallsLastHour: Int? = null,
)

@Serializable
data class DetectorStatsDto(
    @SerialName("inference_speed") val inferenceSpeed: Double? = null,
)

@Serializable
data class ServiceStatsDto(
    val uptime: Long? = null,
    val version: String? = null,
    @SerialName("latest_version") val latestVersion: String? = null,
    val storage: Map<String, StorageStatsDto> = emptyMap(),
    @SerialName("last_updated") val lastUpdated: Long? = null,
)

/** Sizes in MB. */
@Serializable
data class StorageStatsDto(
    val total: Double? = null,
    val used: Double? = null,
    val free: Double? = null,
)
