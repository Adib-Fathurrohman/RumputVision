package com.rumput.vision.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rumput.vision.R
import com.rumput.vision.core.design.component.frigateErrorMessage
import com.rumput.vision.core.design.theme.RumputTheme
import com.rumput.vision.domain.model.AccessCheck
import com.rumput.vision.domain.model.ConnectionRoute
import com.rumput.vision.domain.model.ConnectionTestReport
import com.rumput.vision.domain.model.EndpointTestOutcome
import com.rumput.vision.domain.model.EndpointTestResult

private enum class Severity { OK, WARNING, ERROR }

@Composable
internal fun ConnectionTestResults(report: ConnectionTestReport) {
    Column(Modifier.fillMaxWidth().testTag("test_results"), verticalArrangement = Arrangement.spacedBy(RumputTheme.spacing.sm)) {
        if (report.results.isEmpty()) {
            ResultLine(Severity.ERROR, stringResource(R.string.test_nothing_to_test))
        }
        report.results.forEach { ResultCard(it) }
    }
}

@Composable
private fun ResultCard(result: EndpointTestResult) {
    val spacing = RumputTheme.spacing
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Text(
            stringResource(if (result.route == ConnectionRoute.LOCAL) R.string.route_local else R.string.route_remote) +
                " · " + result.url,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (val outcome = result.outcome) {
            is EndpointTestOutcome.Reachable -> {
                ResultLine(
                    Severity.OK,
                    outcome.version?.let { stringResource(R.string.test_reachable_version, it) }
                        ?: stringResource(R.string.test_reachable),
                )
                AccessLine(outcome.access)
            }
            is EndpointTestOutcome.Redirected -> ResultLine(
                Severity.ERROR,
                stringResource(R.string.test_redirected, outcome.location ?: "?"),
            )
            EndpointTestOutcome.NotFrigate -> ResultLine(Severity.ERROR, stringResource(R.string.test_not_frigate))
            is EndpointTestOutcome.Failed -> ResultLine(Severity.ERROR, frigateErrorMessage(outcome.error))
        }
    }
}

@Composable
private fun AccessLine(access: AccessCheck) = when (access) {
    is AccessCheck.Anonymous ->
        if (access.isAdmin) {
            ResultLine(Severity.WARNING, stringResource(R.string.test_anonymous_admin))
        } else {
            ResultLine(Severity.OK, stringResource(R.string.test_anonymous, access.role ?: "?"))
        }
    is AccessCheck.SignedIn -> ResultLine(
        Severity.OK,
        stringResource(R.string.test_signed_in, access.username ?: "?", access.role ?: "?"),
    )
    AccessCheck.SignInRequired -> ResultLine(Severity.WARNING, stringResource(R.string.test_sign_in_required))
    AccessCheck.InvalidCredentials -> ResultLine(Severity.ERROR, stringResource(R.string.sign_in_invalid))
    AccessCheck.RateLimited -> ResultLine(Severity.ERROR, stringResource(R.string.error_rate_limited))
    AccessCheck.ServerRequiresLogin -> ResultLine(Severity.ERROR, stringResource(R.string.test_server_requires_login))
    is AccessCheck.Failed -> ResultLine(Severity.ERROR, frigateErrorMessage(access.error))
}

@Composable
private fun ResultLine(severity: Severity, text: String) {
    val (icon: ImageVector, tint: Color) = when (severity) {
        Severity.OK -> Icons.Outlined.CheckCircle to RumputTheme.statusColors.online
        Severity.WARNING -> Icons.Outlined.WarningAmber to RumputTheme.statusColors.warning
        Severity.ERROR -> Icons.Outlined.ErrorOutline to MaterialTheme.colorScheme.error
    }
    Row(horizontalArrangement = Arrangement.spacedBy(RumputTheme.spacing.sm)) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp).padding(top = 1.dp))
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}
