package com.rumput.vision.feature.settings

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rumput.vision.BuildConfig
import com.rumput.vision.core.common.Clock
import com.rumput.vision.core.logging.DiagnosticsLog
import com.rumput.vision.core.network.NetworkMonitor
import com.rumput.vision.core.network.NetworkStatus
import com.rumput.vision.domain.model.ConnectionState
import com.rumput.vision.domain.model.ServerProfile
import com.rumput.vision.domain.repository.AppPreferencesRepository
import com.rumput.vision.domain.repository.ServerConnection
import com.rumput.vision.domain.repository.ServerProfileRepository
import com.rumput.vision.domain.repository.ServerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DiagnosticsUiState(
    val snapshot: DiagnosticsSnapshot? = null,
) {
    val report: String? get() = snapshot?.let(DiagnosticsReport::build)
}

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    profiles: ServerProfileRepository,
    connection: ServerConnection,
    private val server: ServerRepository,
    preferences: AppPreferencesRepository,
    network: NetworkMonitor,
    private val clock: Clock,
) : ViewModel() {

    private data class Basics(
        val profile: ServerProfile?,
        val connection: ConnectionState,
        val network: NetworkStatus?,
    )

    private val basics = combine(
        profiles.observeActiveProfile(),
        connection.state,
        network.status.onStart<NetworkStatus?> { emit(null) },
        ::Basics,
    )

    val uiState: StateFlow<DiagnosticsUiState> = combine(
        basics,
        server.observeServerStatus(),
        server.observeCameras(),
        preferences.preferences,
        DiagnosticsLog.entries,
    ) { b, status, cameras, prefs, log ->
        DiagnosticsUiState(
            DiagnosticsSnapshot(
                appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                buildType = BuildConfig.BUILD_TYPE,
                android = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                device = "${Build.MANUFACTURER} ${Build.MODEL}",
                profile = b.profile,
                connection = b.connection,
                network = b.network,
                server = status,
                cameras = cameras,
                preferences = prefs,
                log = log,
                generatedAtEpochMillis = clock.nowEpochMillis(),
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DiagnosticsUiState())

    init {
        // One fetch so server and camera stats are current when the screen opens; nothing polls.
        viewModelScope.launch { server.refresh() }
    }
}
