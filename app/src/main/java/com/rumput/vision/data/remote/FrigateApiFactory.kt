package com.rumput.vision.data.remote

import com.rumput.vision.core.network.FrigateJson
import com.rumput.vision.domain.model.ServerUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Inject

class FrigateApiFactory @Inject constructor() {
    fun create(baseUrl: ServerUrl, client: OkHttpClient): FrigateApi = Retrofit.Builder()
        .baseUrl("${baseUrl.value}/")
        .client(client)
        .addConverterFactory(FrigateJson.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(FrigateApi::class.java)
}
