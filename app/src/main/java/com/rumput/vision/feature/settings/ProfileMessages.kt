package com.rumput.vision.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.rumput.vision.R
import com.rumput.vision.domain.model.AuthMode
import com.rumput.vision.domain.model.EndpointPreference
import com.rumput.vision.domain.usecase.FormError
import com.rumput.vision.domain.usecase.NameError
import com.rumput.vision.domain.usecase.UrlError
import com.rumput.vision.domain.usecase.UrlWarning
import com.rumput.vision.domain.usecase.ValidateServerProfileUseCase

@Composable
internal fun urlErrorText(error: UrlError): String = stringResource(
    when (error) {
        UrlError.EMPTY -> R.string.error_url_empty
        UrlError.MISSING_SCHEME -> R.string.error_url_missing_scheme
        UrlError.UNSUPPORTED_SCHEME -> R.string.error_url_unsupported_scheme
        UrlError.MALFORMED -> R.string.error_url_malformed
        UrlError.CONTAINS_CREDENTIALS -> R.string.error_url_contains_credentials
        UrlError.CONTAINS_QUERY_OR_FRAGMENT -> R.string.error_url_query
        UrlError.MISSING_HOST -> R.string.error_url_missing_host
        UrlError.INVALID_PORT -> R.string.error_url_invalid_port
        UrlError.HTTPS_REQUIRED_FOR_LOGIN -> R.string.error_url_https_required
        UrlError.CLEARTEXT_TO_PUBLIC_HOST -> R.string.error_url_cleartext_public
    },
)

@Composable
internal fun urlWarningText(warning: UrlWarning): String = stringResource(
    when (warning) {
        UrlWarning.CLEARTEXT -> R.string.warning_cleartext
        UrlWarning.UNAUTHENTICATED_INTERNAL_PORT -> R.string.warning_port_5000
        UrlWarning.REMOTE_URL_IS_LAN_ONLY -> R.string.warning_remote_lan_only
    },
)

@Composable
internal fun nameErrorText(error: NameError): String = when (error) {
    NameError.REQUIRED -> stringResource(R.string.error_name_required)
    NameError.TOO_LONG -> pluralStringResource(
        R.plurals.error_name_too_long,
        ValidateServerProfileUseCase.MAX_NAME_LENGTH,
        ValidateServerProfileUseCase.MAX_NAME_LENGTH,
    )
}

@Composable
internal fun formErrorText(error: FormError): String = stringResource(
    when (error) {
        FormError.AT_LEAST_ONE_URL -> R.string.error_at_least_one_url
        FormError.LOCAL_URL_REQUIRED_FOR_PREFERENCE -> R.string.error_local_required
        FormError.REMOTE_URL_REQUIRED_FOR_PREFERENCE -> R.string.error_remote_required
    },
)

@Composable
internal fun endpointLabel(preference: EndpointPreference): String = stringResource(
    when (preference) {
        EndpointPreference.AUTOMATIC -> R.string.endpoint_automatic
        EndpointPreference.LOCAL_ONLY -> R.string.endpoint_local
        EndpointPreference.REMOTE_ONLY -> R.string.endpoint_remote
    },
)

@Composable
internal fun authLabel(mode: AuthMode): String = stringResource(
    when (mode) {
        AuthMode.FRIGATE_LOGIN -> R.string.auth_frigate_login
        AuthMode.NONE -> R.string.auth_none
    },
)
