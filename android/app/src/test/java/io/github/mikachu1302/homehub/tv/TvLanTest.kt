package io.github.mikachu1302.homehub.tv

import io.github.mikachu1302.homehub.net.NotLanException
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

/**
 * The TV code opens its own sockets; each must refuse addresses outside the LAN before connecting (AGENTS.md).
 * Addresses are documentation ranges (made up); the accepted case is a fake TV on loopback.
 */
class TvLanTest {
    @Test
    fun httpToAnAddressOutsideTheLanIsRefusedBeforeConnecting() {
        for (host in listOf("198.51.100.7", "203.0.113.20")) {
            try {
                httpRequest(host, 80, "GET / HTTP/1.1\r\nHost: $host\r\n\r\n", timeoutMs = 300)
                fail("$host was not refused")
            } catch (e: NotLanException) {
                // expected
            }
        }
    }

    @Test
    fun httpOnTheLanWorks() {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            val tv = thread {
                server.accept().use { s ->
                    s.getInputStream().read(ByteArray(1024))
                    s.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok".toByteArray())
                }
            }
            val answer = httpRequest("127.0.0.1", server.localPort, "GET / HTTP/1.1\r\nHost: tv\r\n\r\n")
            tv.join(2_000)
            assertEquals(200, answer.status)
            assertEquals("ok", answer.body)
        }
    }
}
