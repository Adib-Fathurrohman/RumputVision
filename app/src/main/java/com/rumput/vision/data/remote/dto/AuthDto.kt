package com.rumput.vision.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `POST /api/login` body (`AppPostLoginBody` in the OpenAPI spec). */
@Serializable
data class LoginRequestDto(val user: String, val password: String) {
    override fun toString(): String = "LoginRequestDto(user=$user, password=***)"
}

/** `GET /api/profile`. */
@Serializable
data class ProfileDto(
    val username: String? = null,
    val role: String? = null,
    @SerialName("allowed_cameras") val allowedCameras: List<String> = emptyList(),
)
