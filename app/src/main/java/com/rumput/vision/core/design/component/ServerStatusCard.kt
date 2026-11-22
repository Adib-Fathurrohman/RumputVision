package com.rumput.vision.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rumput.vision.R
import com.rumput.vision.core.design.theme.RumputTheme
import com.rumput.vision.core.design.theme.tabular
import com.rumput.vision.domain.model.ConnectionRoute
import com.rumput.vision.domain.model.ConnectionState
import com.rumput.vision.domain.model.ServerStatus

/** Server identity, connection and health. [status] is null until live data has been fetched. */
@Composable
fun ServerStatusCard(
    serverName: String,
    connection: ConnectionState,
    status: ServerStatus?,
    usesCleartext: Boolean,
    modifier: Modifier = Modifier,
) {
    val spacing = RumputTheme.spacing
    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(serverName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            ConnectionIndicator(connection)
        }
        if (connection is ConnectionState.Connected) {
            val route = stringResource(
                if (connection.route == ConnectionRoute.LOCAL) R.string.route_local else R.string.route_remote,
            )
            Text(
                "$route · ${connection.serverUrl.value}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (status != null) {
            MetricRow(stringResource(R.string.status_version), status.version ?: "—")
            if (status.updateAvailable) {
                MetricRow(stringResource(R.string.status_update_available), status.latestVersion.orEmpty())
            }
            status.uptimeSeconds?.let { MetricRow(stringResource(R.string.status_uptime), formatUptime(it)) }
            status.detectors.forEach { detector ->
                MetricRow(
                    stringResource(R.string.status_detector, detector.name),
                    detector.inferenceSpeedMs?.let { "%.1f ms".format(it) } ?: "—",
                )
            }
            status.recordingStorage?.let { storage ->
                MetricRow(
                    stringResource(R.string.status_storage),
                    "${formatStorageMb(storage.usedMb)} / ${formatStorageMb(storage.totalMb)}",
                )
                LinearProgressIndicator(
                    progress = { storage.fraction },
                    modifier = Modifier.fillMaxWidth(),
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    drawStopIndicator = {},
                )
            }
        }
        if (connection is ConnectionState.Connected && connection.anonymousAdmin) {
            WarningText(stringResource(R.string.warning_anonymous_admin))
        }
        if (usesCleartext) WarningText(stringResource(R.string.warning_cleartext_short))
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.bodySmall.tabular)
    }
}

@Composable
fun WarningText(text: String, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(RumputTheme.spacing.sm)) {
        Icon(
            Icons.Outlined.WarningAmber,
            contentDescription = null,
            tint = RumputTheme.statusColors.warning,
            modifier = Modifier.size(16.dp).padding(top = 1.dp),
        )
        Text(text, style = MaterialTheme.typography.bodySmall, color = RumputTheme.statusColors.warning)
    }
}
