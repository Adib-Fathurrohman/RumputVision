package com.rumput.vision.domain.usecase

import com.rumput.vision.domain.model.AuthMode
import com.rumput.vision.domain.model.HostClass
import com.rumput.vision.domain.model.ServerUrl
import java.net.URI
import java.net.URISyntaxException

/**
 * Parses server URLs and applies the transport security policy from docs/SECURITY.md §2.3.
 * Pure Kotlin and free of DNS lookups, so it is safe on the main thread and in JVM tests.
 */
object ServerUrlPolicy {

    const val FRIGATE_INTERNAL_UNAUTHENTICATED_PORT = 5000

    sealed interface ParseResult {
        data class Valid(val url: ServerUrl) : ParseResult
        data class Invalid(val error: UrlError) : ParseResult
    }

    fun parse(raw: String): ParseResult {
        val input = raw.trim()
        if (input.isEmpty()) return ParseResult.Invalid(UrlError.EMPTY)

        val schemeEnd = input.indexOf("://")
        if (schemeEnd <= 0) return ParseResult.Invalid(UrlError.MISSING_SCHEME)
        val scheme = input.substring(0, schemeEnd).lowercase()
        if (scheme != "http" && scheme != "https") return ParseResult.Invalid(UrlError.UNSUPPORTED_SCHEME)

        val afterScheme = input.substring(schemeEnd + 3)
        if (afterScheme.isEmpty() || afterScheme.startsWith("/")) return ParseResult.Invalid(UrlError.MISSING_HOST)

        val uri = try {
            URI(input)
        } catch (_: URISyntaxException) {
            return ParseResult.Invalid(UrlError.MALFORMED)
        }
        // A non-null raw authority with a null host means the authority could not be parsed as server-based.
        if (uri.rawUserInfo != null || uri.rawAuthority?.contains('@') == true) {
            return ParseResult.Invalid(UrlError.CONTAINS_CREDENTIALS)
        }
        if (uri.rawQuery != null || uri.rawFragment != null) {
            return ParseResult.Invalid(UrlError.CONTAINS_QUERY_OR_FRAGMENT)
        }
        val host = uri.host?.lowercase()?.takeIf { it.isNotBlank() }
            ?: return ParseResult.Invalid(if (uri.rawAuthority.isNullOrEmpty()) UrlError.MISSING_HOST else UrlError.MALFORMED)
        val port = when (val p = uri.port) {
            -1 -> null
            in 1..65535 -> p
            else -> return ParseResult.Invalid(UrlError.INVALID_PORT)
        }
        val path = uri.rawPath.orEmpty().trimEnd('/')

        val value = buildString {
            append(scheme).append("://").append(host)
            if (port != null) append(':').append(port)
            append(path)
        }
        return ParseResult.Valid(
            ServerUrl(
                value = value,
                scheme = scheme,
                host = host,
                port = port,
                hostClass = classifyHost(host),
            ),
        )
    }

    /** Policy errors for a syntactically valid URL used with [authMode]. */
    fun policyError(url: ServerUrl, authMode: AuthMode): UrlError? = when {
        authMode == AuthMode.FRIGATE_LOGIN && !url.isHttps -> UrlError.HTTPS_REQUIRED_FOR_LOGIN
        !url.isHttps && url.hostClass == HostClass.PUBLIC -> UrlError.CLEARTEXT_TO_PUBLIC_HOST
        else -> null
    }

    fun warnings(url: ServerUrl, role: UrlRole): Set<UrlWarning> = buildSet {
        if (!url.isHttps) add(UrlWarning.CLEARTEXT)
        if (url.effectivePort == FRIGATE_INTERNAL_UNAUTHENTICATED_PORT) add(UrlWarning.UNAUTHENTICATED_INTERNAL_PORT)
        if (role == UrlRole.REMOTE && url.hostClass == HostClass.PRIVATE_NETWORK) add(UrlWarning.REMOTE_URL_IS_LAN_ONLY)
    }

    fun classifyHost(host: String): HostClass {
        val h = host.removePrefix("[").removeSuffix("]").lowercase()
        parseIpv4(h)?.let { return classifyIpv4(it) }
        if (h.contains(':')) return classifyIpv6(h)
        return when {
            h.endsWith(".ts.net") -> HostClass.TAILSCALE
            h == "localhost" || !h.contains('.') -> HostClass.PRIVATE_NETWORK
            PRIVATE_NAME_SUFFIXES.any { h.endsWith(it) } -> HostClass.PRIVATE_NETWORK
            else -> HostClass.PUBLIC
        }
    }

    private val PRIVATE_NAME_SUFFIXES = listOf(".home.arpa", ".local", ".lan", ".internal", ".localhost")

    private fun parseIpv4(h: String): IntArray? {
        val parts = h.split('.')
        if (parts.size != 4) return null
        return IntArray(4) { i ->
            val part = parts[i]
            if (part.isEmpty() || part.length > 3 || !part.all(Char::isDigit)) return null
            part.toInt().takeIf { it in 0..255 } ?: return null
        }
    }

    private fun classifyIpv4(o: IntArray): HostClass = when {
        o[0] == 10 -> HostClass.PRIVATE_NETWORK
        o[0] == 172 && o[1] in 16..31 -> HostClass.PRIVATE_NETWORK
        o[0] == 192 && o[1] == 168 -> HostClass.PRIVATE_NETWORK
        o[0] == 127 -> HostClass.PRIVATE_NETWORK
        o[0] == 169 && o[1] == 254 -> HostClass.PRIVATE_NETWORK
        o[0] == 100 && o[1] in 64..127 -> HostClass.TAILSCALE
        else -> HostClass.PUBLIC
    }

    private fun classifyIpv6(h: String): HostClass = when {
        h == "::1" -> HostClass.PRIVATE_NETWORK
        h.startsWith("fd7a:115c:a1e0:") -> HostClass.TAILSCALE
        h.startsWith("fc") || h.startsWith("fd") -> HostClass.PRIVATE_NETWORK
        h.startsWith("fe8") || h.startsWith("fe9") || h.startsWith("fea") || h.startsWith("feb") -> HostClass.PRIVATE_NETWORK
        else -> HostClass.PUBLIC
    }
}

enum class UrlRole { LOCAL, REMOTE }

enum class UrlError {
    EMPTY,
    MISSING_SCHEME,
    UNSUPPORTED_SCHEME,
    MALFORMED,
    CONTAINS_CREDENTIALS,
    CONTAINS_QUERY_OR_FRAGMENT,
    MISSING_HOST,
    INVALID_PORT,
    HTTPS_REQUIRED_FOR_LOGIN,
    CLEARTEXT_TO_PUBLIC_HOST,
}

enum class UrlWarning {
    CLEARTEXT,
    UNAUTHENTICATED_INTERNAL_PORT,
    REMOTE_URL_IS_LAN_ONLY,
}
