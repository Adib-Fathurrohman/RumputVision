package com.rumput.vision.feature.settings

import android.content.ClipData
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rumput.vision.BuildConfig
import com.rumput.vision.R
import com.rumput.vision.core.design.component.RumputScreen
import com.rumput.vision.core.design.component.SectionHeader
import com.rumput.vision.core.design.component.formatTimeWithSeconds
import com.rumput.vision.core.design.theme.RumputTheme
import com.rumput.vision.core.design.theme.tabular
import com.rumput.vision.core.logging.DiagnosticsLog
import com.rumput.vision.domain.model.CameraStatus
import com.rumput.vision.domain.model.ConnectionRoute
import com.rumput.vision.domain.model.ConnectionState
import kotlinx.coroutines.launch

@Composable
fun AboutScreen(onBack: () -> Unit, viewModel: DiagnosticsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snapshot = state.snapshot
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val copiedMessage = stringResource(R.string.diagnostics_copied)

    RumputScreen(
        title = stringResource(R.string.settings_about),
        isTopLevel = false,
        onBack = onBack,
        snackbarHostState = snackbar,
        modifier = Modifier.testTag("screen_about"),
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            SectionHeader(stringResource(R.string.about_section_app))
            InfoRow(stringResource(R.string.about_version), "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            InfoRow(stringResource(R.string.about_build_type), BuildConfig.BUILD_TYPE)
            InfoRow(stringResource(R.string.about_application_id), BuildConfig.APPLICATION_ID)

            SectionHeader(stringResource(R.string.about_section_device))
            InfoRow(stringResource(R.string.about_android), "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            InfoRow(stringResource(R.string.about_device), "${Build.MANUFACTURER} ${Build.MODEL}")

            SectionHeader(stringResource(R.string.about_section_diagnostics))
            val unknown = stringResource(R.string.diagnostics_unknown)
            InfoRow(stringResource(R.string.diagnostics_connection), snapshot?.connection?.let { connectionLabel(it) } ?: unknown)
            InfoRow(
                stringResource(R.string.diagnostics_network),
                snapshot?.network?.let {
                    when {
                        !it.isConnected -> stringResource(R.string.diagnostics_network_offline)
                        it.isLocalCapable -> stringResource(R.string.diagnostics_network_local)
                        else -> stringResource(R.string.diagnostics_network_other)
                    }
                } ?: unknown,
            )
            InfoRow(stringResource(R.string.diagnostics_frigate), snapshot?.server?.version ?: unknown)
            snapshot?.cameras?.takeIf { it.isNotEmpty() }?.let { cameras ->
                InfoRow(
                    stringResource(R.string.diagnostics_cameras),
                    "${cameras.count { it.status == CameraStatus.ONLINE }} / ${cameras.size}",
                )
            }
            RecentWarnings(snapshot?.log.orEmpty())

            Column(
                Modifier.padding(horizontal = RumputTheme.spacing.gutter, vertical = RumputTheme.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(RumputTheme.spacing.sm),
            ) {
                FilledTonalButton(
                    onClick = {
                        val report = state.report ?: return@FilledTonalButton
                        scope.launch {
                            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Rumput Vision diagnostics", report)))
                            // Android 13+ shows its own confirmation when something is copied.
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) snackbar.showSnackbar(copiedMessage)
                        }
                    },
                    enabled = snapshot != null,
                    modifier = Modifier.fillMaxWidth().testTag("diagnostics_copy"),
                ) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.diagnostics_copy))
                }
                Text(
                    stringResource(R.string.diagnostics_copy_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SectionHeader(stringResource(R.string.about_section_security))
            Text(
                stringResource(R.string.about_security_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = RumputTheme.spacing.gutter),
            )
            Spacer(Modifier.height(RumputTheme.spacing.xl))
        }
    }
}

@Composable
private fun connectionLabel(state: ConnectionState): String = when (state) {
    is ConnectionState.Connected -> stringResource(
        if (state.route == ConnectionRoute.LOCAL) R.string.connection_connected_local else R.string.connection_connected_remote,
    )
    ConnectionState.Connecting -> stringResource(R.string.connection_connecting)
    ConnectionState.NotConfigured -> stringResource(R.string.connection_not_configured)
    is ConnectionState.Unauthorized -> stringResource(R.string.connection_unauthorized)
    is ConnectionState.Unreachable -> stringResource(R.string.connection_unreachable)
    ConnectionState.Unknown -> stringResource(R.string.connection_unknown)
}

@Composable
private fun RecentWarnings(entries: List<DiagnosticsLog.Entry>) {
    val spacing = RumputTheme.spacing
    Column(
        Modifier.padding(horizontal = spacing.gutter, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Text(stringResource(R.string.diagnostics_recent_warnings), style = MaterialTheme.typography.bodyMedium)
        if (entries.isEmpty()) {
            Text(
                stringResource(R.string.diagnostics_no_warnings),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        entries.takeLast(VISIBLE_WARNINGS).asReversed().forEach { entry ->
            Text(
                "${formatTimeWithSeconds(entry.epochMillis)}  ${DiagnosticsReport.maskText(entry.message)}",
                style = MaterialTheme.typography.bodySmall.tabular,
                color = if (entry.level == DiagnosticsLog.Level.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun InfoRow(label: String, value: String) {
    val spacing = RumputTheme.spacing
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.gutter, vertical = spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium.tabular)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

private const val VISIBLE_WARNINGS = 10
