package com.rumput.vision.domain.usecase

import com.rumput.vision.domain.model.AuthMode
import com.rumput.vision.domain.model.HostClass
import com.rumput.vision.domain.model.ServerUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerUrlPolicyTest {

    private fun valid(raw: String): ServerUrl = when (val r = ServerUrlPolicy.parse(raw)) {
        is ServerUrlPolicy.ParseResult.Valid -> r.url
        is ServerUrlPolicy.ParseResult.Invalid -> throw AssertionError("Expected valid for '$raw', got ${r.error}")
    }

    private fun error(raw: String): UrlError = when (val r = ServerUrlPolicy.parse(raw)) {
        is ServerUrlPolicy.ParseResult.Valid -> throw AssertionError("Expected invalid for '$raw', got ${r.url}")
        is ServerUrlPolicy.ParseResult.Invalid -> r.error
    }

    @Test
    fun `normalises scheme, host case and trailing slash`() {
        val url = valid("  HTTPS://Frigate.Example.COM:8971/ ")
        assertEquals("https://frigate.example.com:8971", url.value)
        assertEquals(8971, url.port)
        assertTrue(url.isHttps)
    }

    @Test
    fun `keeps a reverse-proxy sub path`() {
        assertEquals("https://example.com/frigate", valid("https://example.com/frigate/").value)
    }

    @Test
    fun `default ports are reported as effective ports`() {
        assertEquals(80, valid("http://frigate.home.arpa").effectivePort)
        assertEquals(443, valid("https://frigate.example.com").effectivePort)
    }

    @Test
    fun `rejects malformed input`() {
        assertEquals(UrlError.EMPTY, error("   "))
        assertEquals(UrlError.MISSING_SCHEME, error("frigate.home.arpa"))
        assertEquals(UrlError.MISSING_SCHEME, error("192.168.100.22:5000"))
        assertEquals(UrlError.UNSUPPORTED_SCHEME, error("rtsp://192.168.100.22:8554/garasi"))
        assertEquals(UrlError.UNSUPPORTED_SCHEME, error("ftp://host"))
        assertEquals(UrlError.MALFORMED, error("http://exa mple.com"))
        assertEquals(UrlError.MISSING_HOST, error("https://"))
        assertEquals(UrlError.INVALID_PORT, error("http://host:70000"))
    }

    @Test
    fun `rejects credentials, query and fragment in URLs`() {
        assertEquals(UrlError.CONTAINS_CREDENTIALS, error("https://admin:secret@frigate.example.com"))
        assertEquals(UrlError.CONTAINS_CREDENTIALS, error("https://admin@frigate.example.com"))
        assertEquals(UrlError.CONTAINS_QUERY_OR_FRAGMENT, error("https://frigate.example.com/?token=x"))
        assertEquals(UrlError.CONTAINS_QUERY_OR_FRAGMENT, error("https://frigate.example.com/#live"))
    }

    @Test
    fun `classifies private, tailscale and public hosts`() {
        val cases = mapOf(
            "192.168.100.22" to HostClass.PRIVATE_NETWORK,
            "10.0.0.5" to HostClass.PRIVATE_NETWORK,
            "172.16.0.1" to HostClass.PRIVATE_NETWORK,
            "172.31.255.255" to HostClass.PRIVATE_NETWORK,
            "172.32.0.1" to HostClass.PUBLIC,
            "127.0.0.1" to HostClass.PRIVATE_NETWORK,
            "169.254.1.1" to HostClass.PRIVATE_NETWORK,
            "100.64.0.1" to HostClass.TAILSCALE,
            "100.127.255.254" to HostClass.TAILSCALE,
            "100.128.0.1" to HostClass.PUBLIC,
            "8.8.8.8" to HostClass.PUBLIC,
            "frigate.home.arpa" to HostClass.PRIVATE_NETWORK,
            "nvr.local" to HostClass.PRIVATE_NETWORK,
            "nvr.lan" to HostClass.PRIVATE_NETWORK,
            "frigate" to HostClass.PRIVATE_NETWORK,
            "localhost" to HostClass.PRIVATE_NETWORK,
            "server.tail1234.ts.net" to HostClass.TAILSCALE,
            "frigate.example.com" to HostClass.PUBLIC,
            "[fd00::1]" to HostClass.PRIVATE_NETWORK,
            "[fe80::1]" to HostClass.PRIVATE_NETWORK,
            "[::1]" to HostClass.PRIVATE_NETWORK,
            "[fd7a:115c:a1e0::1]" to HostClass.TAILSCALE,
            "[2001:db8::1]" to HostClass.PUBLIC,
            // Not a valid IPv4 literal, so it is treated as a public DNS name.
            "192.168.1.999" to HostClass.PUBLIC,
        )
        cases.forEach { (host, expected) -> assertEquals(host, expected, ServerUrlPolicy.classifyHost(host)) }
    }

    @Test
    fun `IPv6 literal URLs parse`() {
        val url = valid("http://[fd00::22]:5000")
        assertEquals(HostClass.PRIVATE_NETWORK, url.hostClass)
        assertEquals(5000, url.port)
    }

    @Test
    fun `login requires https on every URL`() {
        assertEquals(UrlError.HTTPS_REQUIRED_FOR_LOGIN, ServerUrlPolicy.policyError(valid("http://frigate.home.arpa"), AuthMode.FRIGATE_LOGIN))
        assertEquals(UrlError.HTTPS_REQUIRED_FOR_LOGIN, ServerUrlPolicy.policyError(valid("http://100.80.1.2"), AuthMode.FRIGATE_LOGIN))
        assertNull(ServerUrlPolicy.policyError(valid("https://frigate.home.arpa"), AuthMode.FRIGATE_LOGIN))
    }

    @Test
    fun `cleartext without auth is allowed only to private and tailscale hosts`() {
        assertNull(ServerUrlPolicy.policyError(valid("http://192.168.100.22"), AuthMode.NONE))
        assertNull(ServerUrlPolicy.policyError(valid("http://frigate.home.arpa"), AuthMode.NONE))
        assertNull(ServerUrlPolicy.policyError(valid("http://100.101.102.103"), AuthMode.NONE))
        assertEquals(UrlError.CLEARTEXT_TO_PUBLIC_HOST, ServerUrlPolicy.policyError(valid("http://frigate.example.com"), AuthMode.NONE))
    }

    @Test
    fun `warnings flag cleartext, port 5000 and LAN-only remote URLs`() {
        assertEquals(
            setOf(UrlWarning.CLEARTEXT, UrlWarning.UNAUTHENTICATED_INTERNAL_PORT),
            ServerUrlPolicy.warnings(valid("http://192.168.100.22:5000"), UrlRole.LOCAL),
        )
        assertEquals(
            setOf(UrlWarning.REMOTE_URL_IS_LAN_ONLY),
            ServerUrlPolicy.warnings(valid("https://frigate.home.arpa"), UrlRole.REMOTE),
        )
        assertEquals(emptySet<UrlWarning>(), ServerUrlPolicy.warnings(valid("https://nvr.tail1234.ts.net"), UrlRole.REMOTE))
    }
}
