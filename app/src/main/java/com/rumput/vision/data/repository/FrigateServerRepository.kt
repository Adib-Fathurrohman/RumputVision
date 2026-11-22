package com.rumput.vision.data.repository

import com.rumput.vision.core.common.Clock
import com.rumput.vision.core.database.CameraDao
import com.rumput.vision.data.mapper.toDomain
import com.rumput.vision.data.mapper.toEntity
import com.rumput.vision.data.mapper.toServerStatus
import com.rumput.vision.data.remote.FrigateSession
import com.rumput.vision.domain.model.CameraStats
import com.rumput.vision.domain.model.CameraStatus
import com.rumput.vision.domain.model.CameraWithStatus
import com.rumput.vision.domain.model.ConnectionState
import com.rumput.vision.domain.model.FrigateError
import com.rumput.vision.domain.model.FrigateResult
import com.rumput.vision.domain.model.ServerStatus
import com.rumput.vision.domain.repository.ServerProfileRepository
import com.rumput.vision.domain.repository.ServerRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FrigateServerRepository @Inject constructor(
    private val session: FrigateSession,
    private val profiles: ServerProfileRepository,
    private val cameraDao: CameraDao,
    private val clock: Clock,
) : ServerRepository {

    /** Result of the last successful refresh. In memory only: live status is never restored from disk. */
    private data class LiveSnapshot(
        val profileId: String,
        val server: ServerStatus,
        val cameraStats: Map<String, CameraStats>,
    )

    private val live = MutableStateFlow<LiveSnapshot?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeCameras(): Flow<List<CameraWithStatus>> = profiles.observeActiveProfile().flatMapLatest { profile ->
        if (profile == null) return@flatMapLatest flowOf(emptyList())
        combine(cameraDao.observeForProfile(profile.id), live, session.state) { entities, snapshot, connection ->
            val fresh = snapshot?.takeIf {
                it.profileId == profile.id &&
                    connection is ConnectionState.Connected &&
                    clock.nowEpochMillis() - it.server.fetchedAtEpochMillis <= STALE_AFTER_MILLIS
            }
            entities.map { entity ->
                val camera = entity.toDomain()
                val stats = fresh?.cameraStats?.get(camera.name)
                val status = when {
                    !camera.enabled -> CameraStatus.DISABLED
                    fresh == null -> CameraStatus.UNKNOWN
                    stats != null && stats.cameraFps > 0.0 -> CameraStatus.ONLINE
                    else -> CameraStatus.OFFLINE
                }
                CameraWithStatus(
                    camera = camera,
                    status = status,
                    stats = stats,
                    lastSeenOnlineEpochMillis = entity.lastSeenOnlineAt,
                    isCached = fresh == null,
                )
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeServerStatus(): Flow<ServerStatus?> = profiles.observeActiveProfile().flatMapLatest { profile ->
        if (profile == null) {
            flowOf(null)
        } else {
            combine(live, session.state) { snapshot, connection ->
                snapshot?.takeIf { it.profileId == profile.id && connection is ConnectionState.Connected }?.server
            }
        }
    }

    override suspend fun refresh(): FrigateResult<Unit> {
        val profile = profiles.observeActiveProfile().first()
            ?: return FrigateResult.Failure(FrigateError.NotConfigured)

        val result = session.execute { api ->
            coroutineScope {
                val config = async { api.config() }
                val stats = async { api.stats() }
                config.await() to stats.await()
            }
        }
        return when (result) {
            is FrigateResult.Failure -> {
                live.value = null
                result
            }
            is FrigateResult.Success -> {
                val (config, stats) = result.value
                val now = clock.nowEpochMillis()
                val previous = cameraDao.getForProfile(profile.id).associateBy { it.name }
                val entities = config.cameras.map { (name, dto) ->
                    val online = (stats.cameras[name]?.cameraFps ?: 0.0) > 0.0
                    dto.toEntity(
                        profileId = profile.id,
                        name = name,
                        lastSeenOnlineAt = if (online) now else previous[name]?.lastSeenOnlineAt,
                        now = now,
                    )
                }
                cameraDao.replaceForProfile(profile.id, entities)
                val version = (session.state.value as? ConnectionState.Connected)?.serverVersion
                live.value = LiveSnapshot(
                    profileId = profile.id,
                    server = stats.toServerStatus(fallbackVersion = version, now = now),
                    cameraStats = stats.cameras.mapValues { it.value.toDomain() },
                )
                FrigateResult.Success(Unit)
            }
        }
    }

    private companion object {
        /** Live status older than this is shown as unknown rather than online/offline. */
        const val STALE_AFTER_MILLIS = 2 * 60 * 1000L
    }
}
