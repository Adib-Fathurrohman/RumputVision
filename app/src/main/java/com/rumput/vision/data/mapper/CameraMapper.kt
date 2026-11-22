package com.rumput.vision.data.mapper

import com.rumput.vision.core.database.CameraEntity
import com.rumput.vision.core.network.FrigateJson
import com.rumput.vision.data.remote.dto.CameraConfigDto
import com.rumput.vision.data.remote.dto.CameraStatsDto
import com.rumput.vision.data.remote.dto.StatsDto
import com.rumput.vision.domain.model.Camera
import com.rumput.vision.domain.model.CameraStats
import com.rumput.vision.domain.model.CameraStream
import com.rumput.vision.domain.model.DetectorStatus
import com.rumput.vision.domain.model.ServerStatus
import com.rumput.vision.domain.model.StorageUsage
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

@Serializable
private data class StoredStream(val label: String, val stream: String)

private val streamListSerializer = ListSerializer(StoredStream.serializer())

fun CameraConfigDto.toEntity(profileId: String, name: String, lastSeenOnlineAt: Long?, now: Long) = CameraEntity(
    profileId = profileId,
    name = name,
    enabled = enabled,
    uiOrder = ui?.order ?: 0,
    showOnDashboard = ui?.dashboard ?: true,
    liveStreams = FrigateJson.encodeToString(
        streamListSerializer,
        live?.streams.orEmpty().map { (label, stream) -> StoredStream(label, stream) },
    ),
    detectWidth = detect?.width,
    detectHeight = detect?.height,
    detectEnabled = detect?.enabled ?: false,
    recordEnabled = record?.enabled ?: false,
    snapshotsEnabled = snapshots?.enabled ?: false,
    audioEnabled = audio?.enabled ?: false,
    lastSeenOnlineAt = lastSeenOnlineAt,
    updatedAt = now,
)

fun CameraEntity.toDomain(): Camera = Camera(
    name = name,
    displayName = cameraDisplayName(name),
    enabled = enabled,
    order = uiOrder,
    showOnDashboard = showOnDashboard,
    liveStreams = runCatching { FrigateJson.decodeFromString(streamListSerializer, liveStreams) }
        .getOrDefault(emptyList())
        .map { CameraStream(it.label, it.stream) },
    detectWidth = detectWidth,
    detectHeight = detectHeight,
    detectEnabled = detectEnabled,
    recordingEnabled = recordEnabled,
    snapshotsEnabled = snapshotsEnabled,
    audioEnabled = audioEnabled,
)

/** Frigate 0.18 has no friendly-name field for cameras: `ruang_tengah` becomes "Ruang Tengah". */
fun cameraDisplayName(name: String): String = name
    .split('_', '-')
    .filter { it.isNotBlank() }
    .joinToString(" ") { word -> word.replaceFirstChar { it.titlecase() } }
    .ifEmpty { name }

fun CameraStatsDto.toDomain() = CameraStats(
    cameraFps = cameraFps,
    expectedFps = expectedFps,
    detectionFps = detectionFps,
    detectionEnabled = detectionEnabled,
    connectionQuality = connectionQuality,
    reconnectsLastHour = reconnectsLastHour,
    stallsLastHour = stallsLastHour,
)

fun StatsDto.toServerStatus(fallbackVersion: String?, now: Long): ServerStatus {
    val recordings = service?.storage?.entries
        ?.firstOrNull { it.key.endsWith("/recordings") }
        ?.value
    return ServerStatus(
        version = service?.version ?: fallbackVersion,
        latestVersion = service?.latestVersion,
        uptimeSeconds = service?.uptime,
        detectors = detectors.map { (name, d) -> DetectorStatus(name, d.inferenceSpeed) }.sortedBy { it.name },
        recordingStorage = recordings?.let { s ->
            val total = s.total
            val used = s.used
            if (total != null && used != null) StorageUsage(usedMb = used, totalMb = total) else null
        },
        fetchedAtEpochMillis = now,
    )
}
