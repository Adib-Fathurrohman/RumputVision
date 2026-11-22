package com.rumput.vision.feature.settings

import com.rumput.vision.core.logging.DiagnosticsLog
import com.rumput.vision.core.network.NetworkStatus
import com.rumput.vision.domain.model.AppPreferences
import com.rumput.vision.domain.model.AuthMode
import com.rumput.vision.domain.model.ConnectionRoute
import com.rumput.vision.domain.model.ConnectionState
import com.rumput.vision.domain.model.DetectorStatus
import com.rumput.vision.domain.model.FrigateError
import com.rumput.vision.domain.model.NetworkFailure
import com.rumput.vision.domain.model.ServerStatus
import com.rumput.vision.testing.profile
import com.rumput.vision.testing.url
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class DiagnosticsReportTest {

    private fun snapshot(
        connection: ConnectionState = ConnectionState.Connected(ConnectionRoute.REMOTE, url("https://nvr.tail1234.ts.net"), "0.18.2", false),
        log: List<DiagnosticsLog.Entry> = emptyList(),
    ) = DiagnosticsSnapshot(
        appVersion = "1.0 (1)",
        buildType = "release",
        android = "16 (API 36)",
        device = "Google Pixel",
        profile = profile(
            local = "http://192.168.1.10",
            remote = "https://nvr.tail1234.ts.net",
            authMode = AuthMode.FRIGATE_LOGIN,
            username = "rumput-admin",
            active = true,
        ),
        connection = connection,
        network = NetworkStatus(isConnected = true, isLocalCapable = false, networkId = "net-42"),
        server = ServerStatus("0.18.2-cfa08c5", "0.18.2", 90_061, listOf(DetectorStatus("onnx", 12.34)), null, 0),
        cameras = emptyList(),
        preferences = AppPreferences(),
        log = log,
        generatedAtEpochMillis = 0,
        zone = ZoneOffset.UTC,
    )

    @Test
    fun reportLeavesOutAddressesAndUsername() {
        val report = DiagnosticsReport.build(snapshot())

        assertFalse(report.contains("192.168"))
        assertFalse(report.contains("tail1234"))
        assertFalse(report.contains("ts.net"))
        assertFalse(report.contains("rumput-admin"))
        assertFalse(report.contains("net-42"))
        assertTrue(report.contains("Local URL: http://<private network>:80"))
        assertTrue(report.contains("Remote URL: https://<tailscale>:443"))
        assertTrue(report.contains("Authentication: FRIGATE_LOGIN (username set)"))
        assertTrue(report.contains("Connection: connected via REMOTE (https://<tailscale>:443)"))
        assertTrue(report.contains("Frigate: 0.18.2-cfa08c5 (latest: 0.18.2)"))
        assertTrue(report.contains("Uptime: 1d 1h"))
        assertTrue(report.contains("Detector onnx: 12.3 ms"))
        assertTrue(report.contains("Generated: 1970-01-01 00:00:00 +0000"))
    }

    @Test
    fun reportsFailureStates() {
        val report = DiagnosticsReport.build(snapshot(ConnectionState.Unreachable(FrigateError.Unreachable(NetworkFailure.TLS))))
        assertTrue(report, report.contains("Connection: unreachable: Unreachable(failure=TLS)"))
    }

    @Test
    fun logLinesAreMasked() {
        val log = listOf(
            DiagnosticsLog.Entry(0, DiagnosticsLog.Level.WARN, "GET http://192.168.1.10:5000/api/stats failed"),
            DiagnosticsLog.Entry(0, DiagnosticsLog.Level.ERROR, "Probe https://nvr.tail1234.ts.net/api/version: Authorization: Bearer abc.def"),
            DiagnosticsLog.Entry(0, DiagnosticsLog.Level.WARN, "Resolved frigate.home.arpa to 10.0.0.5 and fd00::1:2:3"),
        )
        val report = DiagnosticsReport.build(snapshot(log = log))

        assertTrue(report.contains("WARN GET http://<host>:5000/api/stats failed"))
        assertTrue(report.contains("ERROR Probe https://<host>/api/version: Authorization: Bearer ***"))
        assertTrue(report.contains("WARN Resolved <host> to <ip> and <ip>"))
        assertFalse(report.contains("abc.def"))
    }

    @Test
    fun maskTextKeepsVersionsAndFileNames() {
        val text = "Frigate 0.18.2 index.m3u8 garasi_sub at 18:35:14"
        assertEquals(text, DiagnosticsReport.maskText(text))
    }

    @Test
    fun ringBufferKeepsTheNewestEntries() {
        DiagnosticsLog.clear()
        repeat(DiagnosticsLog.CAPACITY + 5) { DiagnosticsLog.record(DiagnosticsLog.Level.WARN, "w$it", epochMillis = it.toLong()) }
        val entries = DiagnosticsLog.entries.value
        assertEquals(DiagnosticsLog.CAPACITY, entries.size)
        assertEquals("w5", entries.first().message)
        assertEquals("w${DiagnosticsLog.CAPACITY + 4}", entries.last().message)
        DiagnosticsLog.clear()
    }
}
