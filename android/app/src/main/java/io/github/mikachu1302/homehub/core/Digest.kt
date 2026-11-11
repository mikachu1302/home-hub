package io.github.mikachu1302.homehub.core

import java.security.MessageDigest

/** A server's digest challenge (`WWW-Authenticate: Digest …`). [qop] is "auth" or null (RFC 2069 form). */
data class DigestChallenge(
    val realm: String,
    val nonce: String,
    val qop: String?,
    val opaque: String?,
    /** "MD5" (the IR bridge) or "SHA-256" (Shelly). */
    val algorithm: String,
)

/**
 * HTTP digest authentication (RFC 2617 / RFC 7616), the login of the IR bridge (ESPHome) and of Shelly devices.
 * Only the parts those devices use: MD5 or SHA-256, qop "auth" or none. Cases: spec/fixtures/digest-auth.json.
 */
object Digest {
    private val ALGORITHMS = mapOf("MD5" to "MD5", "SHA-256" to "SHA-256")

    /** The challenge in [header], or null when it is not digest or asks for something this client cannot do. */
    fun parseChallenge(header: String): DigestChallenge? {
        val t = header.trim()
        if (!t.startsWith("Digest ", ignoreCase = true)) return null
        val p = params(t.substring(7))
        val realm = p["realm"] ?: return null
        val nonce = p["nonce"] ?: return null
        val algorithm = ALGORITHMS[(p["algorithm"] ?: "MD5").uppercase()] ?: return null
        val qop = p["qop"]?.let { offered ->
            if (offered.split(',').any { it.trim().equals("auth", ignoreCase = true) }) "auth" else return null
        }
        return DigestChallenge(realm, nonce, qop, p["opaque"], algorithm)
    }

    /** The `response` value: H(H(user:realm:password):nonce[:nc:cnonce:qop]:H(method:uri)). */
    fun response(
        c: DigestChallenge,
        user: String,
        password: String,
        method: String,
        uri: String,
        nc: String?,
        cnonce: String?,
    ): String {
        val h = { s: String -> hash(c.algorithm, s) }
        val ha1 = h("$user:${c.realm}:$password")
        val ha2 = h("$method:$uri")
        return if (c.qop == null) h("$ha1:${c.nonce}:$ha2") else h("$ha1:${c.nonce}:$nc:$cnonce:${c.qop}:$ha2")
    }

    /** The full `Authorization` header value; [count] is the request number under this nonce (starts at 1). */
    fun authorization(c: DigestChallenge, user: String, password: String, method: String, uri: String, count: Int, cnonce: String): String {
        val nc = if (c.qop == null) null else "%08x".format(count)
        val cn = if (c.qop == null) null else cnonce
        val parts = buildList {
            add("username=${quote(user)}")
            add("realm=${quote(c.realm)}")
            add("nonce=${quote(c.nonce)}")
            add("uri=${quote(uri)}")
            add("algorithm=${c.algorithm}")
            if (c.qop != null) {
                add("qop=${c.qop}")
                add("nc=$nc")
                add("cnonce=${quote(cnonce)}")
            }
            add("response=${quote(response(c, user, password, method, uri, nc, cn))}")
            c.opaque?.let { add("opaque=${quote(it)}") }
        }
        return "Digest " + parts.joinToString(", ")
    }

    private fun hash(algorithm: String, s: String): String =
        MessageDigest.getInstance(algorithm).digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun quote(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    /** `key=value` pairs separated by commas; values may be quoted (and then contain commas). */
    private fun params(s: String): Map<String, String> {
        val out = mutableMapOf<String, String>()
        var i = 0
        while (i < s.length) {
            while (i < s.length && (s[i] == ',' || s[i].isWhitespace())) i++
            val eq = s.indexOf('=', i)
            if (eq < 0) break
            val key = s.substring(i, eq).trim().lowercase()
            i = eq + 1
            val value = StringBuilder()
            if (i < s.length && s[i] == '"') {
                i++
                while (i < s.length && s[i] != '"') {
                    if (s[i] == '\\' && i + 1 < s.length) i++
                    value.append(s[i++])
                }
                i++
            } else {
                while (i < s.length && s[i] != ',') value.append(s[i++])
            }
            out[key] = value.toString().trim()
        }
        return out
    }
}
