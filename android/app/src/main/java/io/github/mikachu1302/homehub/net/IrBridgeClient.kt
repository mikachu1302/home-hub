package io.github.mikachu1302.homehub.net

import io.github.mikachu1302.homehub.core.BridgeLogin
import io.github.mikachu1302.homehub.core.Digest
import io.github.mikachu1302.homehub.core.DigestChallenge
import io.github.mikachu1302.homehub.core.IrKey
import io.github.mikachu1302.homehub.core.irButtonPath
import io.github.mikachu1302.homehub.core.irPressPath
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.security.SecureRandom

/** A bridge problem the user can act on; the message is shown as it is. */
class BridgeException(message: String) : IOException(message)

/**
 * The IR bridge (ESPHome web server, docs/PROTOCOLS.md) with its digest login. The bridge checks digest answers
 * without remembering its nonces, so one challenge is reused for every request (counting `nc` up) and fetched
 * again only when the bridge answers 401. Requests run one at a time, so key presses arrive in order.
 */
class IrBridgeClient(private val http: LanHttp = LanHttp()) {
    private val lock = Mutex()
    private val random = SecureRandom()
    private var challenge: Pair<String, DigestChallenge>? = null
    private var count = 0

    /** Sends [key]'s IR code. */
    suspend fun press(host: String, login: BridgeLogin?, key: IrKey) {
        call(host, login, "POST", irPressPath(key), key)
    }

    /** Checks address, login and that [key] exists, without sending anything to the amp. */
    suspend fun check(host: String, login: BridgeLogin?, key: IrKey) {
        call(host, login, "GET", irButtonPath(key), key)
    }

    private suspend fun call(host: String, login: BridgeLogin?, method: String, path: String, key: IrKey) = lock.withLock {
        val body = if (method == "POST") "" else null
        var fresh = false
        while (true) {
            val c = challenge?.takeIf { it.first == host }?.second
            val headers = if (login != null && c != null) {
                mapOf("Authorization" to Digest.authorization(c, login.user, login.password, method, path, ++count, cnonce()))
            } else {
                emptyMap()
            }
            val a = http.send(host, path, method, body, headers)
            when {
                a.code in 200..299 -> return@withLock
                a.code == 401 -> {
                    if (login == null) throw BridgeException("The bridge asks for a login. Enter it in Settings.")
                    if (fresh) throw BridgeException("User or password is wrong.")
                    val next = a.header("WWW-Authenticate")?.let(Digest::parseChallenge)
                        ?: throw BridgeException("The bridge asks for a login type this app does not support.")
                    challenge = host to next
                    count = 0
                    fresh = true
                }
                a.code == 404 -> throw BridgeException("The bridge has no key \"${key.bridgeName}\". Check its firmware.")
                else -> throw IOException("The bridge answered HTTP ${a.code}")
            }
        }
    }

    private fun cnonce(): String = ByteArray(8).also(random::nextBytes).joinToString("") { "%02x".format(it) }
}
