package com.rumput.vision.navigation

import kotlinx.serialization.Serializable

@Serializable data object OnboardingRoute

@Serializable data object HomeRoute

@Serializable data object CamerasRoute

@Serializable data object EventsRoute

@Serializable data object RecordingsRoute

@Serializable data object SettingsRoute

@Serializable data object AboutRoute

/** [profileId] null creates a new profile. */
@Serializable
data class ServerProfileEditRoute(val profileId: String? = null, val fromOnboarding: Boolean = false)

@Serializable
data class CameraDetailRoute(val cameraName: String)
