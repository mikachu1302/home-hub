package io.github.mikachu1302.homehub.net

import com.sun.net.httpserver.HttpServer
import io.github.mikachu1302.homehub.core.ShellyException
import io.github.mikachu1302.homehub.core.SwitchStatus
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The HTTP client against a fake Shelly on 127.0.0.1 (made-up answers, no real device). JVM networking stands in for
 * Android's here, so this checks our own logic: request format, error handling, deadlines and the LAN-only rule.
 */
class LanHttpTest {
    private lateinit var server: HttpServer
    private val release = CountDownLatch(1)
    private var lastBody = ""
    private var lastMethod = ""
    private var lastType: String? = null

    @Before
    fun start() {
        server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/rpc") { ex ->
            lastMethod = ex.requestMethod
            lastType = ex.requestHeaders.getFirst("Content-Type")
            lastBody = ex.requestBody.bufferedReader().readText()
            val method = JSONObject(lastBody).getString("method")
            val (code, answer) = when (method) {
                "Shelly.GetStatus" -> 200 to """{"id":1,"src":"shellyplus1pm-000000000000","result":{"switch:0":{"id":0,"output":true,"apower":7.6,"voltage":230.0}}}"""
                "Switch.Nope" -> 404 to """{"id":1,"src":"shellyplus1pm-000000000000","error":{"code":404,"message":"No handler for Switch.Nope"}}"""
                else -> 200 to """{"id":1,"result":{}}"""
            }
            val bytes = answer.toByteArray()
            ex.sendResponseHeaders(code, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        server.createContext("/broken") { ex ->
            val bytes = "Internal error".toByteArray()
            ex.sendResponseHeaders(500, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        server.createContext("/hang") { ex ->
            // Accepts the request and never answers, like a device behind a dead VPN.
            release.await(10, TimeUnit.SECONDS)
            ex.close()
        }
        server.executor = java.util.concurrent.Executors.newCachedThreadPool()
        server.start()
    }

    @After
    fun stop() {
        release.countDown()
        server.stop(0)
    }

    private fun http(timeoutMs: Int = 2000) = LanHttp(timeoutMs = timeoutMs, port = server.address.port)

    @Test
    fun rpcIsJsonPost() = runBlocking {
        val result = ShellyClient(http()).switches("127.0.0.1")
        assertEquals(listOf(SwitchStatus(0, true, 7.6, 230.0)), result)
        assertEquals("POST", lastMethod)
        assertEquals("application/json", lastType)
        assertEquals("Shelly.GetStatus", JSONObject(lastBody).getString("method"))
    }

    @Test
    fun deviceErrorWithErrorStatusKeepsMessage() = runBlocking {
        try {
            ShellyJsonCall(http()).call("Switch.Nope")
            fail("expected an error")
        } catch (e: ShellyException) {
            assertEquals("No handler for Switch.Nope", e.message)
        }
    }

    @Test
    fun nonJsonErrorIsIoError() = runBlocking {
        try {
            http().get("127.0.0.1", "/broken")
            fail("expected an error")
        } catch (e: IOException) {
            assertEquals("HTTP 500", e.message)
        }
    }

    @Test
    fun silentDeviceTimesOut() = runBlocking {
        val start = System.nanoTime()
        try {
            http(timeoutMs = 300).get("127.0.0.1", "/hang")
            fail("expected a timeout")
        } catch (_: IOException) {
        }
        val ms = (System.nanoTime() - start) / 1_000_000
        // Deadline is 2 x 300 ms + 500 ms; the server would hold the request for 10 s.
        assertTrue("took $ms ms", ms < 2_000)
    }

    @Test
    fun publicAddressIsRefusedBeforeConnecting() = runBlocking {
        try {
            http().get("8.8.8.8", "/shelly")
            fail("expected a refusal")
        } catch (_: NotLanException) {
        }
    }

    /** Sends one arbitrary RPC method through the real request/response path. */
    private class ShellyJsonCall(private val http: LanHttp) {
        suspend fun call(method: String): JSONObject =
            io.github.mikachu1302.homehub.core.ShellyJson.result(
                http.post("127.0.0.1", "/rpc", io.github.mikachu1302.homehub.core.ShellyJson.request(1, method)),
            )
    }
}
