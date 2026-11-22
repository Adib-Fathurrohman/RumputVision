package com.rumput.vision.testing

import com.rumput.vision.core.security.SecretCipher
import com.rumput.vision.core.security.SecretCipherException
import com.rumput.vision.domain.model.AppPreferences
import com.rumput.vision.domain.model.AuthMode
import com.rumput.vision.domain.model.EndpointPreference
import com.rumput.vision.domain.model.ServerProfile
import com.rumput.vision.domain.model.StreamQuality
import com.rumput.vision.domain.repository.AppPreferencesRepository
import com.rumput.vision.domain.repository.PasswordUpdate
import com.rumput.vision.domain.repository.ServerProfileRepository
import com.rumput.vision.domain.model.CameraWithStatus
import com.rumput.vision.domain.model.ConnectionState
import com.rumput.vision.domain.model.ConnectionTestReport
import com.rumput.vision.domain.model.FrigateResult
import com.rumput.vision.domain.model.ServerStatus
import com.rumput.vision.domain.model.UnauthorizedReason
import com.rumput.vision.domain.repository.ConnectionTester
import com.rumput.vision.domain.repository.ServerConnection
import com.rumput.vision.domain.repository.ServerRepository
import com.rumput.vision.domain.repository.SignInResult
import com.rumput.vision.domain.usecase.ServerProfileDraft
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

fun profile(
    id: String = "p1",
    name: String = "Home",
    local: String? = "http://frigate.home.arpa",
    remote: String? = null,
    authMode: AuthMode = AuthMode.NONE,
    username: String? = null,
    active: Boolean = false,
) = ServerProfile(
    id = id,
    displayName = name,
    localUrl = local?.let(::url),
    remoteUrl = remote?.let(::url),
    authMode = authMode,
    username = username,
    endpointPreference = EndpointPreference.AUTOMATIC,
    isActive = active,
    createdAtEpochMillis = 0,
    updatedAtEpochMillis = 0,
)

@Singleton
class FakeServerProfileRepository @Inject constructor() : ServerProfileRepository {
    val profiles = MutableStateFlow<List<ServerProfile>>(emptyList())
    val passwords = mutableMapOf<String, String>()
    val lastPasswordUpdate = mutableMapOf<String, PasswordUpdate>()

    override fun observeProfiles(): Flow<List<ServerProfile>> = profiles

    override fun observeActiveProfile(): Flow<ServerProfile?> = profiles.map { list -> list.firstOrNull { it.isActive } }

    override suspend fun getProfile(id: String): ServerProfile? = profiles.value.firstOrNull { it.id == id }

    override suspend fun saveProfile(profile: ServerProfile, password: PasswordUpdate) {
        lastPasswordUpdate[profile.id] = password
        when (password) {
            PasswordUpdate.Keep -> Unit
            PasswordUpdate.Clear, PasswordUpdate.ClearAll -> passwords.remove(profile.id)
            is PasswordUpdate.Set -> passwords[profile.id] = password.value
        }
        profiles.update { list ->
            // Mirrors ServerProfileDao.upsertEnsuringActive: active if nothing else is active.
            val othersActive = list.any { it.isActive && it.id != profile.id }
            val saved = profile.copy(isActive = profile.isActive || !othersActive)
            if (list.any { it.id == profile.id }) list.map { if (it.id == profile.id) saved else it } else list + saved
        }
    }

    override suspend fun deleteProfile(id: String) {
        passwords.remove(id)
        profiles.update { list ->
            val remaining = list.filterNot { it.id == id }
            if (remaining.isNotEmpty() && remaining.none { it.isActive }) {
                listOf(remaining.first().copy(isActive = true)) + remaining.drop(1)
            } else {
                remaining
            }
        }
    }

    override suspend fun setActiveProfile(id: String) {
        profiles.update { list -> list.map { it.copy(isActive = it.id == id) } }
    }

    override suspend fun hasStoredPassword(id: String): Boolean = passwords.containsKey(id)

    override suspend fun hasStoredCredentials(id: String): Boolean = passwords.containsKey(id)

    override suspend fun clearCredentials(id: String) {
        passwords.remove(id)
    }
}

class FakeServerConnection(initial: ConnectionState = ConnectionState.Unknown) : ServerConnection {
    override val state = MutableStateFlow(initial)
    var reconnects = 0
    var signInResult: SignInResult = SignInResult.Success
    val signIns = mutableListOf<Pair<String, Boolean>>()

    override fun reconnect() {
        reconnects++
    }

    override suspend fun signIn(password: String, rememberPassword: Boolean): SignInResult {
        signIns += password to rememberPassword
        return signInResult
    }

    override suspend fun signOut() {
        state.value = ConnectionState.Unauthorized(UnauthorizedReason.SIGN_IN_REQUIRED)
    }
}

class FakeServerRepository : ServerRepository {
    val cameras = MutableStateFlow<List<CameraWithStatus>>(emptyList())
    val status = MutableStateFlow<ServerStatus?>(null)
    var refreshResult: FrigateResult<Unit> = FrigateResult.Success(Unit)
    var refreshCount = 0

    override fun observeCameras(): Flow<List<CameraWithStatus>> = cameras
    override fun observeServerStatus(): Flow<ServerStatus?> = status
    override suspend fun refresh(): FrigateResult<Unit> {
        refreshCount++
        return refreshResult
    }
}

class FakeConnectionTester(var report: ConnectionTestReport = ConnectionTestReport(emptyList())) : ConnectionTester {
    val tested = mutableListOf<ServerProfileDraft>()
    override suspend fun test(draft: ServerProfileDraft): ConnectionTestReport {
        tested += draft
        return report
    }
}

@Singleton
class FakeAppPreferencesRepository @Inject constructor() : AppPreferencesRepository {
    private val state = MutableStateFlow(AppPreferences())
    override val preferences: Flow<AppPreferences> = state

    override suspend fun setGridStreamQuality(quality: StreamQuality) = state.update { it.copy(gridStreamQuality = quality) }
    override suspend fun setFullscreenStreamQuality(quality: StreamQuality) = state.update { it.copy(fullscreenStreamQuality = quality) }
    override suspend fun setMaxConcurrentGridStreams(count: Int) = state.update {
        it.copy(maxConcurrentGridStreams = count.coerceIn(AppPreferences.MIN_GRID_STREAMS, AppPreferences.MAX_GRID_STREAMS))
    }
    override suspend fun setStartMuted(muted: Boolean) = state.update { it.copy(startMuted = muted) }
    override suspend fun setInAppEventUpdates(enabled: Boolean) = state.update { it.copy(inAppEventUpdates = enabled) }
    override suspend fun setEventNotificationsEnabled(enabled: Boolean) = state.update { it.copy(eventNotificationsEnabled = enabled) }
}

/**
 * Stand-in for the Keystore cipher: reversible, but it authenticates the associated data
 * and a checksum so tamper and swap cases behave like AES-GCM.
 */
class FakeSecretCipher : SecretCipher {
    var failAll = false

    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray {
        val tag = (plaintext.contentHashCode() * 31 + associatedData.contentHashCode()).toString().toByteArray()
        return byteArrayOf(tag.size.toByte()) + tag + plaintext.map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
    }

    override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray): ByteArray {
        if (failAll || ciphertext.isEmpty()) throw SecretCipherException("unavailable")
        val tagLen = ciphertext[0].toInt()
        if (ciphertext.size < 1 + tagLen) throw SecretCipherException("short")
        val tag = ciphertext.copyOfRange(1, 1 + tagLen)
        val plaintext = ciphertext.copyOfRange(1 + tagLen, ciphertext.size).map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
        val expected = (plaintext.contentHashCode() * 31 + associatedData.contentHashCode()).toString().toByteArray()
        if (!tag.contentEquals(expected)) throw SecretCipherException("bad tag")
        return plaintext
    }
}

class FakeCredentialStore : com.rumput.vision.core.security.CredentialStore {
    val values = java.util.concurrent.ConcurrentHashMap<String, com.rumput.vision.core.security.StoredCredentials>()

    override suspend fun read(profileId: String) = values[profileId]

    override suspend fun update(
        profileId: String,
        transform: (com.rumput.vision.core.security.StoredCredentials?) -> com.rumput.vision.core.security.StoredCredentials?,
    ) {
        val next = transform(values[profileId])?.takeUnless { it.isEmpty }
        if (next == null) values.remove(profileId) else values[profileId] = next
    }

    override suspend fun delete(profileId: String) {
        values.remove(profileId)
    }
}

class FakeCameraMedia(var baseUrl: String? = null) : com.rumput.vision.domain.repository.CameraMedia {
    override fun latestFrameUrl(cameraName: String, height: Int): String? =
        baseUrl?.let { "$it/api/$cameraName/latest.webp?height=$height" }

    override fun liveStreamUrl(streamName: String): String? =
        baseUrl?.let { "${it.replace("http", "ws")}/live/mse/api/ws?src=$streamName" }

    override fun eventThumbnailUrl(eventId: String) = baseUrl?.let { "$it/api/events/$eventId/thumbnail.webp" }

    override fun eventSnapshotUrl(eventId: String, height: Int) = baseUrl?.let { "$it/api/events/$eventId/snapshot.jpg?height=$height" }

    override fun recordingPlaylistUrl(camera: String, startEpochSeconds: Long, endEpochSeconds: Long) =
        baseUrl?.let { "$it/vod/$camera/start/$startEpochSeconds/end/$endEpochSeconds/index.m3u8" }
}

/** Records calls; tests drive [state] directly. */
class FakeStreamPlayer : com.rumput.vision.core.streaming.CameraStreamPlayer {
    override val state = MutableStateFlow<com.rumput.vision.core.streaming.StreamState>(com.rumput.vision.core.streaming.StreamState.Idle)
    override val rebufferingFrequently = MutableStateFlow(false)
    override val player: androidx.media3.common.Player? = null
    var source: com.rumput.vision.core.streaming.StreamSource? = null
    var playing = false
    var muted: Boolean? = null
    var released = false
    var retries = 0
    val prepared = mutableListOf<com.rumput.vision.core.streaming.StreamSource>()

    override fun prepare(source: com.rumput.vision.core.streaming.StreamSource) {
        if (source != this.source) prepared += source
        this.source = source
    }
    override fun play() { playing = true }
    override fun stop() { playing = false }
    override fun retry() { retries++ }
    override fun setMuted(muted: Boolean) { this.muted = muted }
    override fun release() { released = true; playing = false }
}

class FakeStreamPlayerFactory : com.rumput.vision.core.streaming.CameraStreamPlayerFactory {
    val created = mutableListOf<FakeStreamPlayer>()
    val profiles = mutableListOf<com.rumput.vision.core.streaming.BufferProfile>()
    override fun create(profile: com.rumput.vision.core.streaming.BufferProfile) = FakeStreamPlayer().also {
        created += it
        profiles += profile
    }
}

fun cameraWithStatus(
    name: String,
    status: com.rumput.vision.domain.model.CameraStatus = com.rumput.vision.domain.model.CameraStatus.ONLINE,
    cached: Boolean = false,
) = CameraWithStatus(
    camera = com.rumput.vision.domain.model.Camera(
        name = name,
        displayName = com.rumput.vision.data.mapper.cameraDisplayName(name),
        enabled = status != com.rumput.vision.domain.model.CameraStatus.DISABLED,
        order = 0,
        showOnDashboard = true,
        liveStreams = listOf(com.rumput.vision.domain.model.CameraStream("${name}_sub", "${name}_sub")),
        mainStreamName = "${name}_main",
        detectWidth = 2560,
        detectHeight = 1440,
        detectEnabled = false,
        recordingEnabled = true,
        snapshotsEnabled = false,
        audioEnabled = false,
    ),
    status = status,
    stats = null,
    lastSeenOnlineEpochMillis = null,
    isCached = cached,
)

fun event(id: String, camera: String = "garasi", start: Long = 1_000_000, end: Long? = start + 35_000) =
    com.rumput.vision.domain.model.Event(
        id = id, camera = camera, label = "orang_terdeteksi", subLabel = null, startEpochMillis = start,
        endEpochMillis = end, hasClip = true, hasSnapshot = true, zones = emptyList(), score = null, retained = false,
    )

class FakeEventRepository : com.rumput.vision.domain.repository.EventRepository {
    val cached = MutableStateFlow<List<com.rumput.vision.domain.model.Event>>(emptyList())
    /** Server-side events, newest first. */
    var remote: List<com.rumput.vision.domain.model.Event> = emptyList()
    var failWith: com.rumput.vision.domain.model.FrigateError? = null
    val requests = mutableListOf<Pair<com.rumput.vision.domain.model.EventFilter, Long?>>()
    override val eventActivity = kotlinx.coroutines.flow.MutableSharedFlow<Unit>()

    override fun observeCachedEvents(filter: com.rumput.vision.domain.model.EventFilter, limit: Int) =
        cached.map { list -> list.filter(filter::matches).take(limit) }

    override suspend fun fetchEvents(
        filter: com.rumput.vision.domain.model.EventFilter,
        beforeEpochMillis: Long?,
        limit: Int,
    ): FrigateResult<com.rumput.vision.domain.model.EventPage> {
        requests += filter to beforeEpochMillis
        failWith?.let { return FrigateResult.Failure(it) }
        val page = remote.filter(filter::matches).filter { beforeEpochMillis == null || it.startEpochMillis < beforeEpochMillis }.take(limit)
        return FrigateResult.Success(com.rumput.vision.domain.model.EventPage(page, page.lastOrNull()?.startEpochMillis?.takeIf { page.size >= limit }))
    }

    override suspend fun getEvent(id: String): FrigateResult<com.rumput.vision.domain.model.Event> =
        (remote + cached.value).firstOrNull { it.id == id }?.let { FrigateResult.Success(it) }
            ?: FrigateResult.Failure(com.rumput.vision.domain.model.FrigateError.Http(404))

    override suspend fun labels(): FrigateResult<List<String>> = FrigateResult.Success(listOf("orang_terdeteksi"))
}

class FakeRecordingRepository : com.rumput.vision.domain.repository.RecordingRepository {
    var days: List<com.rumput.vision.domain.model.RecordingDay> = emptyList()
    var segments: List<com.rumput.vision.domain.model.RecordingSegment> = emptyList()
    val dayRequests = mutableListOf<String>()
    override suspend fun recordingDays(camera: String): FrigateResult<List<com.rumput.vision.domain.model.RecordingDay>> {
        dayRequests += camera
        return FrigateResult.Success(days)
    }
    override suspend fun segments(camera: String, startEpochMillis: Long, endEpochMillis: Long) = FrigateResult.Success(segments)
}

class FakeRecordingPlayer : com.rumput.vision.core.streaming.RecordingPlayer {
    override val state = MutableStateFlow<com.rumput.vision.core.streaming.PlaybackState>(com.rumput.vision.core.streaming.PlaybackState.Idle)
    override val player: androidx.media3.common.Player? = null
    var loaded: String? = null
    var released = false
    override var positionMillis = 0L
    override fun load(playlistUrl: String) { loaded = playlistUrl; state.value = com.rumput.vision.core.streaming.PlaybackState.Loading }
    override fun play() { (state.value as? com.rumput.vision.core.streaming.PlaybackState.Ready)?.let { state.value = it.copy(playing = true) } }
    override fun pause() { (state.value as? com.rumput.vision.core.streaming.PlaybackState.Ready)?.let { state.value = it.copy(playing = false) } }
    override fun seekTo(positionMillis: Long) { this.positionMillis = positionMillis.coerceAtLeast(0) }
    override fun release() { released = true }
}

class FakeRecordingPlayerFactory : com.rumput.vision.core.streaming.RecordingPlayerFactory {
    val created = mutableListOf<FakeRecordingPlayer>()
    override fun create() = FakeRecordingPlayer().also { created += it }
}
