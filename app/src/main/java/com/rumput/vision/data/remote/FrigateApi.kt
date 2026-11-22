package com.rumput.vision.data.remote

import com.rumput.vision.data.remote.dto.ConfigDto
import com.rumput.vision.data.remote.dto.ProfileDto
import com.rumput.vision.data.remote.dto.StatsDto
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET

/**
 * Frigate HTTP API, verified against 0.18.2 (see docs/FRIGATE_API.md).
 * Paths are relative to the server base URL. Login lives in [com.rumput.vision.data.remote.auth.FrigateLoginService]
 * because it needs raw response headers.
 */
interface FrigateApi {
    /** `text/plain`, e.g. `0.18.2-cfa08c5`. */
    @GET("api/version")
    suspend fun version(): ResponseBody

    @GET("api/profile")
    suspend fun profile(): ProfileDto

    @GET("api/config")
    suspend fun config(): ConfigDto

    @GET("api/stats")
    suspend fun stats(): StatsDto

    /** Clears the session cookie server-side. Frigate answers with a redirect, so the raw response is returned. */
    @GET("api/logout")
    suspend fun logout(): Response<Unit>
}
