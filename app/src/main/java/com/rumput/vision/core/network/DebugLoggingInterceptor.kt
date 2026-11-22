package com.rumput.vision.core.network

import com.rumput.vision.core.logging.AppLogger
import okhttp3.Interceptor
import okhttp3.Response

/** Logs method, path and status only. Never headers, query strings or bodies. Debug builds only (via AppLogger). */
class DebugLoggingInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val start = System.nanoTime()
        val target = "${request.method} ${request.url.scheme}://${request.url.host}:${request.url.port}${request.url.encodedPath}"
        return try {
            chain.proceed(request).also { response ->
                AppLogger.d { "$target -> ${response.code} (${(System.nanoTime() - start) / 1_000_000} ms)" }
            }
        } catch (e: Exception) {
            AppLogger.e(e) { "$target failed" }
            throw e
        }
    }
}
