package io.github.mikachu1302.homehub.net

import io.github.mikachu1302.homehub.core.isLanAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Thrown when a host resolves to anything outside the local network. */
class NotLanException(host: String) : IOException("$host is not on the local network")

/** Status, headers (names in lower case) and body of an answer. */
class HttpAnswer(val code: Int, val headers: Map<String, List<String>>, val body: String) {
    fun header(name: String): String? = headers[name.lowercase()]?.firstOrNull()
}

/**
 * The only HTTP client in the app (AGENTS.md, hard rules): resolves the host, refuses it unless every
 * address it resolves to is on the LAN, then connects to that checked address itself (not the name again),
 * without following redirects.
 *
 * Every request has a hard deadline ([timeoutMs] for connecting, [timeoutMs] for the answer, plus a margin).
 * When it passes, or the caller is cancelled, the connection is closed, so a dead network (no Wi-Fi, VPN without
 * internet) can never leave a request hanging.
 */
class LanHttp(private val timeoutMs: Int = 3000, private val port: Int = 80) {

    suspend fun get(host: String, path: String): String = checked(send(host, path, "GET", null))

    suspend fun post(host: String, path: String, body: String): String = checked(send(host, path, "POST", body))

    /**
     * A request whose status the caller handles itself (the bridge login answers 401 with a challenge). A POST with
     * an empty [body] still sends `Content-Length: 0`: the bridge refuses a POST without it (HTTP 411).
     */
    suspend fun send(host: String, path: String, method: String, body: String?, headers: Map<String, String> = emptyMap()): HttpAnswer {
        val deadline = timeoutMs * 2L + 500
        return withTimeoutOrNull(deadline) { exchange(host, path, method, body, headers) }
            ?: throw IOException("$host did not answer in time")
    }

    // A JSON body is passed on even with an error status: Shelly reports RPC errors that way.
    private fun checked(a: HttpAnswer): String {
        if (a.code !in 200..299 && !a.body.trimStart().startsWith("{")) throw IOException("HTTP ${a.code}")
        return a.body
    }

    private suspend fun exchange(host: String, path: String, method: String, body: String?, headers: Map<String, String>): HttpAnswer {
        val address = withContext(Dispatchers.IO) { resolveLan(host) }
        val literal = if (address is Inet6Address) "[${address.hostAddress?.substringBefore('%')}]" else address.hostAddress
        val conn = URL("http", literal, port, path).openConnection() as HttpURLConnection
        conn.connectTimeout = timeoutMs
        conn.readTimeout = timeoutMs
        conn.instanceFollowRedirects = false
        conn.requestMethod = method
        conn.useCaches = false
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        return suspendCancellableCoroutine { cont ->
            // Closing the connection is what unblocks a thread stuck in connect or read.
            cont.invokeOnCancellation { conn.disconnect() }
            Dispatchers.IO.asExecutor().execute {
                val result = runCatching { blockingExchange(conn, body) }
                conn.disconnect()
                // Ignored when the request was already cancelled.
                result.fold({ cont.resume(it) }, { cont.resumeWithException(it) })
            }
        }
    }

    private fun blockingExchange(conn: HttpURLConnection, body: String?): HttpAnswer {
        if (body != null) {
            val bytes = body.toByteArray()
            conn.doOutput = true
            conn.setFixedLengthStreamingMode(bytes.size)
            if (bytes.isNotEmpty()) conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(bytes) }
        }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
        val headers = conn.headerFields.filterKeys { it != null }.mapKeys { it.key.lowercase() }
        return HttpAnswer(code, headers, text)
    }

    companion object {
        /** All addresses of [host] must be on the LAN; IPv4 is preferred. */
        fun resolveLan(host: String): InetAddress {
            val all = InetAddress.getAllByName(host)
            if (all.isEmpty() || all.any { !isLanAddress(it.address) }) throw NotLanException(host)
            return all.firstOrNull { it is Inet4Address } ?: all.first()
        }
    }
}
