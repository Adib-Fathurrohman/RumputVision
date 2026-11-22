package com.rumput.vision.data.remote

import com.rumput.vision.data.remote.dto.ConfigDto
import com.rumput.vision.data.remote.dto.EventDto
import com.rumput.vision.data.remote.dto.RecordingDayDto
import com.rumput.vision.data.remote.dto.RecordingSegmentDto
import com.rumput.vision.data.remote.dto.ProfileDto
import com.rumput.vision.data.remote.dto.StatsDto
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

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

    /** Newest first. Filters are comma-separated lists; times are epoch seconds. */
    @GET("api/events")
    suspend fun events(
        @Query("cameras") cameras: String? = null,
        @Query("labels") labels: String? = null,
        @Query("after") after: Double? = null,
        @Query("before") before: Double? = null,
        @Query("limit") limit: Int,
    ): List<EventDto>

    @GET("api/events/{id}")
    suspend fun event(@Path("id") id: String): EventDto

    @GET("api/labels")
    suspend fun labels(): List<String>

    @GET("api/{camera}/recordings/summary")
    suspend fun recordingDays(@Path("camera") camera: String, @Query("timezone") timezone: String): List<RecordingDayDto>

    @GET("api/{camera}/recordings")
    suspend fun recordingSegments(
        @Path("camera") camera: String,
        @Query("after") after: Long,
        @Query("before") before: Long,
    ): List<RecordingSegmentDto>

    /** Clears the session cookie server-side. Frigate answers with a redirect, so the raw response is returned. */
    @GET("api/logout")
    suspend fun logout(): Response<Unit>
}
