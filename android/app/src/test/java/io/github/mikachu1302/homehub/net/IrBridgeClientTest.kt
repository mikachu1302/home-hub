package io.github.mikachu1302.homehub.net

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.github.mikachu1302.homehub.core.AmpKeys
import io.github.mikachu1302.homehub.core.BridgeLogin
import io.github.mikachu1302.homehub.core.IrKey
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.util.UUID

/**
 * The bridge client against a fake ESPHome bridge on 127.0.0.1 that behaves like the real one (ESPHome 2026.10,
 * web_server_idf): a fresh nonce with every 401, digest answers checked without remembering nonces, 411 for a POST
 * without Content-Length, 404 for an unknown button. Made-up login. The fake checks digest with its own code, not
 * the app's.
 */
class IrBridgeClientTest {
    private lateinit var server: HttpServer
    private val login = BridgeLogin("bridge-user", "not-the-real-password")
    private var requests = 0
    private val pressed = mutableListOf<String>()

    @Before
    fun start() {
        server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/button/") { ex -> handle(ex) }
        server.start()
    }

    @After
    fun stop() = server.stop(0)

    private fun handle(ex: HttpExchange) {
        requests++
        val path = ex.requestURI.rawPath
        val auth = ex.requestHeaders.getFirst("Authorization")
        when {
            auth == null || !valid(auth, ex.requestMethod) -> {
                ex.responseHeaders.add(
                    "WWW-Authenticate",
                    """Digest realm="Login Required", qop="auth", nonce="${hex()}", opaque="${hex()}"""",
                )
                reply(ex, 401)
            }
            ex.requestMethod == "POST" && ex.requestHeaders.getFirst("Content-Length") == null -> reply(ex, 411)
            !path.startsWith("/button/Amp%20") -> reply(ex, 404)
            ex.requestMethod == "POST" -> {
                pressed += path
                reply(ex, 200)
            }
            else -> reply(ex, 200, """{"id":"button-amp_volume_up","name":"Amp Volume Up"}""")
        }
    }

    private fun valid(header: String, method: String): Boolean {
        val p = Regex("""(\w+)=(?:"([^"]*)"|([^,\s]+))""").findAll(header.removePrefix("Digest "))
            .associate { it.groupValues[1] to (it.groupValues[2].ifEmpty { it.groupValues[3] }) }
        if (p["username"] != login.user) return false
        val ha1 = md5("${login.user}:${p["realm"]}:${login.password}")
        val ha2 = md5("$method:${p["uri"]}")
        return p["response"] == md5("$ha1:${p["nonce"]}:${p["nc"]}:${p["cnonce"]}:${p["qop"]}:$ha2")
    }

    private fun reply(ex: HttpExchange, code: Int, body: String = "") {
        val bytes = body.toByteArray()
        ex.sendResponseHeaders(code, if (bytes.isEmpty()) -1 else bytes.size.toLong())
        ex.responseBody.use { it.write(bytes) }
    }

    private fun md5(s: String) = MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun hex() = UUID.randomUUID().toString().replace("-", "")

    private fun client() = IrBridgeClient(LanHttp(timeoutMs = 2000, port = server.address.port))

    @Test
    fun loginOnceThenOneRequestPerPress() = runBlocking {
        val c = client()
        c.press("127.0.0.1", login, AmpKeys.volumeUp)
        assertEquals("challenge + press", 2, requests)
        c.press("127.0.0.1", login, AmpKeys.volumeDown)
        c.press("127.0.0.1", login, AmpKeys.mute)
        assertEquals("the challenge is reused", 4, requests)
        assertEquals(
            listOf("/button/Amp%20Volume%20Up/press", "/button/Amp%20Volume%20Down/press", "/button/Amp%20Mute/press"),
            pressed,
        )
    }

    @Test
    fun checkSendsNothing() = runBlocking {
        client().check("127.0.0.1", login, AmpKeys.volumeUp)
        assertEquals(emptyList<String>(), pressed)
    }

    @Test
    fun wrongPassword() = runBlocking {
        expect("The bridge did not accept the login.") { client().press("127.0.0.1", login.copy(password = "wrong"), AmpKeys.mute) }
        assertEquals("one challenge, one failed answer", 2, requests)
    }

    @Test
    fun noLogin() = runBlocking {
        expect("The bridge asks for a login. Enter it in Settings.") { client().press("127.0.0.1", null, AmpKeys.mute) }
    }

    @Test
    fun unknownKey() = runBlocking {
        expect("The bridge has no key \"Tv Power\". Check its firmware.") {
            client().press("127.0.0.1", login, IrKey("TV power", "Tv Power"))
        }
    }

    private suspend fun expect(message: String, block: suspend () -> Unit) {
        try {
            block()
            fail("expected: $message")
        } catch (e: BridgeException) {
            assertEquals(message, e.message)
        }
    }
}
