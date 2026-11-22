package com.rumput.vision.core.streaming

import okhttp3.HttpUrl
import okhttp3.OkHttpClient

/** Supplies the HTTP client for a media URL (authenticated only for the connected server). */
fun interface StreamClientProvider {
    fun mediaClientFor(url: HttpUrl): OkHttpClient
}
