package com.rumput.vision.data.remote.auth

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.Cookie
import okhttp3.Headers
import okhttp3.HttpUrl
import java.util.Base64

/** Frigate's default `auth.cookie_name`. */
const val DEFAULT_FRIGATE_COOKIE_NAME = "frigate_token"

object SessionToken {
    /**
     * Extracts the session JWT from `Set-Cookie` headers. Prefers [cookieName]; otherwise accepts a single
     * cookie whose value looks like a JWT (covers servers with a custom `auth.cookie_name`).
     */
    fun fromSetCookie(url: HttpUrl, headers: Headers, cookieName: String = DEFAULT_FRIGATE_COOKIE_NAME): String? {
        val cookies = Cookie.parseAll(url, headers).filter { it.value.isNotEmpty() }
        cookies.firstOrNull { it.name == cookieName }?.let { return it.value }
        return cookies.filter { looksLikeJwt(it.value) }.singleOrNull()?.value
    }

    /** `exp` claim in epoch seconds, or null if the token is not a readable JWT. The signature is not checked. */
    fun expiresAtEpochSeconds(token: String): Long? = try {
        val payload = token.split('.').takeIf { it.size == 3 }?.get(1) ?: return null
        val json = String(Base64.getUrlDecoder().decode(payload.padBase64()), Charsets.UTF_8)
        Json.parseToJsonElement(json).jsonObject["exp"]?.jsonPrimitive?.longOrNull
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun looksLikeJwt(value: String) = value.count { it == '.' } == 2 && value.startsWith("eyJ")

    private fun String.padBase64(): String = this + "=".repeat((4 - length % 4) % 4)
}
