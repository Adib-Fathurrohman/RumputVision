package com.rumput.vision.testing

import com.rumput.vision.core.network.ForegroundMonitor
import com.rumput.vision.core.network.NetworkMonitor
import com.rumput.vision.core.network.NetworkStatus
import com.rumput.vision.core.network.baseOkHttpClient
import com.rumput.vision.domain.model.ServerUrl
import kotlinx.coroutines.flow.MutableStateFlow
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList

object Fixtures {
    fun read(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource("frigate/0.18.2/$name")) { "Missing fixture $name" }.readText()
}

object TestTls {
    private val localhost = HeldCertificate.Builder()
        .addSubjectAlternativeName("localhost")
        .addSubjectAlternativeName("127.0.0.1")
        .build()
    val server: HandshakeCertificates = HandshakeCertificates.Builder().heldCertificate(localhost).build()
    val client: HandshakeCertificates = HandshakeCertificates.Builder().addTrustedCertificate(localhost.certificate).build()

    /** The production base client (transport guard included) that also trusts the test certificate. */
    fun clientTrustingTestCa(): OkHttpClient = baseOkHttpClient().newBuilder()
        .sslSocketFactory(client.sslSocketFactory(), client.trustManager)
        .build()
}

fun jwt(expEpochSeconds: Long, subject: String = "admin"): String {
    val enc = Base64.getUrlEncoder().withoutPadding()
    val header = enc.encodeToString("""{"alg":"HS256","typ":"JWT"}""".toByteArray())
    val payload = enc.encodeToString("""{"sub":"$subject","exp":$expEpochSeconds}""".toByteArray())
    return "$header.$payload.c2lnbmF0dXJl"
}

/**
 * A scriptable Frigate stand-in serving the recorded 0.18.2 fixtures.
 * [requireAuth] mimics port 8971: API calls without a valid Bearer token get 401.
 */
class FakeFrigateServer(
    https: Boolean = false,
    var requireAuth: Boolean = false,
    var validPassword: String = "correct horse",
    var validToken: String = jwt(4_102_444_800), // 2100-01-01
) : AutoCloseable {
    val server = MockWebServer()
    val requests = CopyOnWriteArrayList<RecordedRequest>()
    var loginStatus: Int? = null
    var statsOverride: String? = null
    var configOverride: String? = null

    /** When set, the next authorized API response re-issues the session cookie with this token (Frigate's refresh). */
    var refreshTokenTo: String? = null

    init {
        if (https) server.useHttps(TestTls.server.sslSocketFactory())
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                return respond(request)
            }
        }
        server.start()
    }

    fun url(): ServerUrl = url(server.url("/").toString().trimEnd('/'))

    fun paths(): List<String> = requests.map { it.url.encodedPath }

    private fun respond(request: RecordedRequest): MockResponse {
        val path = request.url.encodedPath
        if (path == "/api/login") {
            loginStatus?.let { return MockResponse.Builder().code(it).build() }
            val body = request.body?.utf8().orEmpty()
            return if (body.contains("\"password\":\"$validPassword\"")) {
                MockResponse.Builder()
                    .code(200)
                    .addHeader("Set-Cookie", "frigate_token=$validToken; Path=/; HttpOnly; Secure")
                    .body("{}")
                    .build()
            } else {
                MockResponse.Builder().code(401).body("""{"message":"Login failed"}""").build()
            }
        }
        val authorized = !requireAuth || request.headers["Authorization"] == "Bearer $validToken"
        if (!authorized) return MockResponse.Builder().code(401).build()
        val response = route(path)
        val refreshed = refreshTokenTo ?: return response
        refreshTokenTo = null
        validToken = refreshed
        return response.newBuilder().addHeader("Set-Cookie", "frigate_token=$refreshed; Path=/; HttpOnly").build()
    }

    private fun route(path: String): MockResponse {
        return when (path) {
            "/api/version" -> text(Fixtures.read("version.txt"))
            "/api/profile" -> json(
                if (requireAuth) """{"username":"admin","role":"admin","allowed_cameras":[]}""" else Fixtures.read("profile.json"),
            )
            "/api/config" -> json(configOverride ?: Fixtures.read("config.json"))
            "/api/stats" -> json(statsOverride ?: Fixtures.read("stats.json"))
            "/api/logout" -> MockResponse.Builder().code(303).addHeader("Location", "/login").build()
            else -> MockResponse.Builder().code(404).body("""{"detail":"Not Found"}""").build()
        }
    }

    private fun text(body: String) = MockResponse.Builder().code(200).addHeader("Content-Type", "text/plain").body(body).build()
    private fun json(body: String) = MockResponse.Builder().code(200).addHeader("Content-Type", "application/json").body(body).build()

    override fun close() = server.close()
}

class FakeNetworkMonitor(initial: NetworkStatus = WIFI) : NetworkMonitor {
    override val status = MutableStateFlow(initial)

    companion object {
        val WIFI = NetworkStatus(isConnected = true, isLocalCapable = true, networkId = "wifi")
        val CELLULAR = NetworkStatus(isConnected = true, isLocalCapable = false, networkId = "cell")
    }
}

class FakeForegroundMonitor(initial: Boolean = true) : ForegroundMonitor {
    override val isForeground = MutableStateFlow(initial)
}

val NetworkStatusDisconnected = NetworkStatus.Disconnected

/** An address on localhost where nothing listens, for "unreachable" cases. */
fun closedPortUrl(): ServerUrl {
    val port = java.net.ServerSocket(0).use { it.localPort }
    return url("http://127.0.0.1:$port")
}

fun url(raw: String): ServerUrl = (com.rumput.vision.domain.usecase.ServerUrlPolicy.parse(raw) as
    com.rumput.vision.domain.usecase.ServerUrlPolicy.ParseResult.Valid).url
