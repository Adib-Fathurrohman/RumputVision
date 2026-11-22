package com.rumput.vision.feature.settings

import com.rumput.vision.core.logging.DiagnosticsLog
import com.rumput.vision.core.logging.Redactor
import com.rumput.vision.core.network.NetworkStatus
import com.rumput.vision.domain.model.AppPreferences
import com.rumput.vision.domain.model.CameraWithStatus
import com.rumput.vision.domain.model.ConnectionState
import com.rumput.vision.domain.model.HostClass
import com.rumput.vision.domain.model.ServerProfile
import com.rumput.vision.domain.model.ServerStatus
import com.rumput.vision.domain.model.ServerUrl
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Everything the diagnostics report is built from; collected by [DiagnosticsViewModel]. */
data class DiagnosticsSnapshot(
    val appVersion: String,
    val buildType: String,
    val android: String,
    val device: String,
    val profile: ServerProfile?,
    val connection: ConnectionState,
    val network: NetworkStatus?,
    val server: ServerStatus?,
    val cameras: List<CameraWithStatus>,
    val preferences: AppPreferences,
    val log: List<DiagnosticsLog.Entry>,
    val generatedAtEpochMillis: Long,
    val zone: ZoneId = ZoneId.systemDefault(),
)

/**
 * Plain-text report for "Copy diagnostics", safe to paste into an issue: it never contains passwords,
 * tokens or the username, and server addresses are reduced to scheme, kind of host and port
 * ("http://<private network>:80"). Log lines are masked the same way.
 */
object DiagnosticsReport {

    fun build(s: DiagnosticsSnapshot): String = buildString {
        line("Rumput Vision diagnostics")
        line("Generated: ${time(s.generatedAtEpochMillis, s.zone)}")
        line()
        line("[App]")
        line("Version: ${s.appVersion} (${s.buildType})")
        line("Android: ${s.android}")
        line("Device: ${s.device}")
        line()
        line("[Network]")
        line("Network: ${network(s.network)}")
        line()
        line("[Server profile]")
        val p = s.profile
        if (p == null) {
            line("No server configured")
        } else {
            line("Local URL: ${p.localUrl?.let(::maskUrl) ?: "not set"}")
            line("Remote URL: ${p.remoteUrl?.let(::maskUrl) ?: "not set"}")
            line("Endpoint preference: ${p.endpointPreference}")
            line("Authentication: ${p.authMode}${if (p.username != null) " (username set)" else ""}")
        }
        line("Connection: ${connection(s.connection)}")
        line()
        line("[Server]")
        val server = s.server
        if (server == null) {
            line("Not fetched")
        } else {
            line("Frigate: ${server.version ?: "unknown"}${server.latestVersion?.let { " (latest: $it)" }.orEmpty()}")
            server.uptimeSeconds?.let { line("Uptime: ${duration(it)}") }
            server.detectors.forEach { d ->
                line("Detector ${d.name}: ${d.inferenceSpeedMs?.let { String.format(Locale.US, "%.1f ms", it) } ?: "n/a"}")
            }
            server.recordingStorage?.let {
                line(String.format(Locale.US, "Recording storage: %.0f%% of %.0f GB", it.fraction * 100, it.totalMb / 1024))
            }
        }
        line()
        line("[Cameras] ${s.cameras.size}")
        s.cameras.forEach { line(camera(it)) }
        line()
        line("[Streaming settings]")
        with(s.preferences) {
            line("Grid: $gridStreamQuality, up to $maxConcurrentGridStreams live; full screen: $fullscreenStreamQuality; start muted: $startMuted")
            line("In-app event updates: $inAppEventUpdates")
        }
        line()
        line("[Recent warnings] ${s.log.size}")
        if (s.log.isEmpty()) line("None")
        s.log.forEach { line("${time(it.epochMillis, s.zone)} ${it.level} ${maskText(it.message)}") }
    }

    /** `http://<private network>:80`: keeps what matters for support (scheme, kind of host, port), not the address. */
    fun maskUrl(url: ServerUrl): String = "${url.scheme}://<${hostLabel(url.hostClass)}>:${url.effectivePort}"

    /** [Redactor] plus masking of URL hosts, IP addresses and host names in free text. */
    fun maskText(text: String): String = Redactor.redact(text)
        .replace(URL_HOST, "$1<host>")
        .replace(IPV4, "<ip>")
        .replace(IPV6, "<ip>")
        .replace(HOST_NAME, "<host>")

    private fun hostLabel(hostClass: HostClass) = when (hostClass) {
        HostClass.PRIVATE_NETWORK -> "private network"
        HostClass.TAILSCALE -> "tailscale"
        HostClass.PUBLIC -> "public host"
    }

    private fun network(status: NetworkStatus?) = when {
        status == null -> "unknown"
        !status.isConnected -> "offline"
        status.isLocalCapable -> "Wi-Fi or Ethernet"
        else -> "mobile or other (local URL not tried)"
    }

    private fun connection(state: ConnectionState) = when (state) {
        ConnectionState.NotConfigured -> "not configured"
        ConnectionState.Connecting -> "connecting"
        ConnectionState.Unknown -> "not checked yet"
        is ConnectionState.Connected -> buildString {
            append("connected via ${state.route} (${maskUrl(state.serverUrl)})")
            if (state.anonymousAdmin) append(", anonymous admin (authentication bypassed)")
        }
        is ConnectionState.Unauthorized -> "sign-in needed: ${state.reason}"
        is ConnectionState.Unreachable -> "unreachable: ${state.error}"
    }

    private fun camera(c: CameraWithStatus) = buildString {
        append("- ${c.camera.name}: ${c.status}")
        if (!c.camera.enabled) append(", disabled")
        c.stats?.let { st ->
            append(String.format(Locale.US, ", %.1f fps", st.cameraFps))
            st.expectedFps?.let { append(String.format(Locale.US, " of %.0f", it)) }
            if (st.detectionEnabled) append(String.format(Locale.US, ", detect %.1f fps", st.detectionFps))
            st.connectionQuality?.let { append(", link $it") }
            st.reconnectsLastHour?.takeIf { it > 0 }?.let { append(", $it reconnects/h") }
            st.stallsLastHour?.takeIf { it > 0 }?.let { append(", $it stalls/h") }
        }
        append(", streams: ${c.camera.liveStreams.joinToString("/") { it.streamName }.ifEmpty { "none" }}")
        if (c.isCached) append(" (cached)")
    }

    private fun duration(seconds: Long): String {
        val days = seconds / 86_400
        val hours = seconds % 86_400 / 3_600
        val minutes = seconds % 3_600 / 60
        return if (days > 0) "${days}d ${hours}h" else "${hours}h ${minutes}m"
    }

    private fun time(epochMillis: Long, zone: ZoneId) = TIME.format(Instant.ofEpochMilli(epochMillis).atZone(zone))

    private fun StringBuilder.line(text: String = "") = append(text).append('\n')

    private val TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss xx", Locale.US)
    private val URL_HOST = Regex("""(\b[a-zA-Z][a-zA-Z0-9+.-]*://)[^/\s:?#"']+""")
    private val IPV4 = Regex("""\b(?:\d{1,3}\.){3}\d{1,3}\b""")
    /** Compressed ("fd00::1") or full eight-group IPv6; clock times like 18:35:14 do not match. */
    private val IPV6 = Regex("""(?<![\w:])(?=[0-9a-fA-F:]*::|(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4})[0-9a-fA-F:]{3,}(?![\w:])""")

    /** Dotted names ending in a letter TLD (frigate.home.arpa, x.ts.net); not version numbers or file names like index.m3u8. */
    private val HOST_NAME = Regex("""\b(?:[a-zA-Z0-9-]+\.)+(?:arpa|lan|local|home|internal|net|com|org|io|dev|app|id)\b""")
}
