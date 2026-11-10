package io.github.mikachu1302.homehub.net

import io.github.mikachu1302.homehub.core.isLanAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URL

/** Thrown when a host resolves to anything outside the local network. */
class NotLanException(host: String) : IOException("$host is not on the local network")

/**
 * The only HTTP client in the app (AGENTS.md, hard rules): resolves the host, refuses it unless every
 * address it resolves to is on the LAN, then connects to that checked address itself (not the name again),
 * on port 80, without following redirects.
 */
class LanHttp(private val timeoutMs: Int = 3000) {

    suspend fun get(host: String, path: String): String = request(host, path, "GET", null)

    suspend fun post(host: String, path: String, body: String): String = request(host, path, "POST", body)

    private suspend fun request(host: String, path: String, method: String, body: String?): String =
        withContext(Dispatchers.IO) {
            val address = resolveLan(host)
            val literal = if (address is Inet6Address) "[${address.hostAddress?.substringBefore('%')}]" else address.hostAddress
            val conn = URL("http", literal, 80, path).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = timeoutMs
                conn.readTimeout = timeoutMs
                conn.instanceFollowRedirects = false
                conn.requestMethod = method
                conn.useCaches = false
                if (body != null) {
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.outputStream.use { it.write(body.toByteArray()) }
                }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
                if (code !in 200..299 && !text.trimStart().startsWith("{")) throw IOException("HTTP $code")
                text
            } finally {
                conn.disconnect()
            }
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
