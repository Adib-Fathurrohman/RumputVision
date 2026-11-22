package com.rumput.vision.domain.model

/**
 * A validated, normalised server base URL (no trailing slash, no query, no user-info).
 * Instances are created only by [com.rumput.vision.domain.usecase.ServerUrlPolicy].
 */
@ConsistentCopyVisibility
data class ServerUrl internal constructor(
    val value: String,
    val scheme: String,
    val host: String,
    val port: Int?,
    val hostClass: HostClass,
) {
    val isHttps: Boolean get() = scheme == "https"

    /** Port actually used for the connection, including the scheme default. */
    val effectivePort: Int get() = port ?: if (isHttps) 443 else 80

    override fun toString(): String = value
}

enum class HostClass {
    /** Loopback, RFC 1918, link-local, IPv6 ULA, or a home/LAN-only name such as *.home.arpa. */
    PRIVATE_NETWORK,

    /** Tailscale CGNAT range 100.64.0.0/10 or a MagicDNS *.ts.net name. */
    TAILSCALE,

    PUBLIC,
}
