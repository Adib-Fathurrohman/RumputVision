package com.rumput.vision.feature.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.rumput.vision.BuildConfig
import com.rumput.vision.R
import com.rumput.vision.core.design.component.RumputScreen
import com.rumput.vision.core.design.component.SectionHeader
import com.rumput.vision.core.design.theme.RumputTheme
import com.rumput.vision.core.design.theme.tabular

@Composable
fun AboutScreen(onBack: () -> Unit) {
    RumputScreen(
        title = stringResource(R.string.settings_about),
        isTopLevel = false,
        onBack = onBack,
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

            SectionHeader(stringResource(R.string.about_section_security))
            Text(
                stringResource(R.string.about_security_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = RumputTheme.spacing.gutter),
            )
        }
    }
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
