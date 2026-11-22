package com.rumput.vision.data.mapper

import com.rumput.vision.core.database.ServerProfileEntity
import com.rumput.vision.domain.model.AuthMode
import com.rumput.vision.domain.model.EndpointPreference
import com.rumput.vision.domain.model.ServerProfile
import com.rumput.vision.domain.model.ServerUrl
import com.rumput.vision.domain.usecase.ServerUrlPolicy

fun ServerProfile.toEntity(): ServerProfileEntity = ServerProfileEntity(
    id = id,
    displayName = displayName,
    localUrl = localUrl?.value,
    remoteUrl = remoteUrl?.value,
    authMode = authMode.name,
    username = username,
    endpointPreference = endpointPreference.name,
    isActive = isActive,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

/** Stored URLs are re-parsed, so a stored value that no longer passes validation is dropped, not trusted. */
fun ServerProfileEntity.toDomain(): ServerProfile = ServerProfile(
    id = id,
    displayName = displayName,
    localUrl = localUrl?.toServerUrlOrNull(),
    remoteUrl = remoteUrl?.toServerUrlOrNull(),
    authMode = AuthMode.entries.firstOrNull { it.name == authMode } ?: AuthMode.FRIGATE_LOGIN,
    username = username,
    endpointPreference = EndpointPreference.entries.firstOrNull { it.name == endpointPreference }
        ?: EndpointPreference.AUTOMATIC,
    isActive = isActive,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

private fun String.toServerUrlOrNull(): ServerUrl? =
    (ServerUrlPolicy.parse(this) as? ServerUrlPolicy.ParseResult.Valid)?.url
