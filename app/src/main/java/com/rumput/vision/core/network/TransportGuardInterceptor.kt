package com.rumput.vision.core.network

import com.rumput.vision.domain.model.HostClass
import com.rumput.vision.domain.usecase.ServerUrlPolicy
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class InsecureTransportException(message: String) : IOException(message)

/**
 * Last line of defence for docs/SECURITY.md §2.3, applied to every request regardless of profile settings:
 * plain HTTP may never carry credentials and may only reach private-network or Tailscale hosts.
 */
class TransportGuardInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!request.url.isHttps) {
            if (request.header("Authorization") != null || request.header("Cookie") != null) {
                throw InsecureTransportException("Refusing to send credentials over plain HTTP")
            }
            if (isLoginPath(request.url.encodedPath)) {
                throw InsecureTransportException("Refusing to sign in over plain HTTP")
            }
            if (ServerUrlPolicy.classifyHost(request.url.host) == HostClass.PUBLIC) {
                throw InsecureTransportException("Plain HTTP is only allowed to private-network hosts")
            }
        }
        return chain.proceed(request)
    }

    private fun isLoginPath(path: String) = path.trimEnd('/').endsWith("/api/login")
}
