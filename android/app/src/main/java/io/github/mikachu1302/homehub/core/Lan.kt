package io.github.mikachu1302.homehub.core

/**
 * True when [address] (4 or 16 raw bytes) can only be on the local network or this device:
 * IPv4 10/8, 172.16/12, 192.168/16, link-local 169.254/16, loopback 127/8;
 * IPv6 unique-local fc00::/7, link-local fe80::/10, loopback ::1, and IPv4-mapped forms of the above.
 * The LAN-only HTTP client refuses every other address (AGENTS.md, hard rules).
 */
fun isLanAddress(address: ByteArray): Boolean {
    fun u(i: Int) = address[i].toInt() and 0xff
    return when (address.size) {
        4 -> u(0) == 10 ||
            (u(0) == 172 && u(1) in 16..31) ||
            (u(0) == 192 && u(1) == 168) ||
            (u(0) == 169 && u(1) == 254) ||
            u(0) == 127
        16 -> {
            val loopback = (0 until 15).all { u(it) == 0 } && u(15) == 1
            val mapped = (0 until 10).all { u(it) == 0 } && u(10) == 0xff && u(11) == 0xff
            when {
                loopback -> true
                mapped -> isLanAddress(address.copyOfRange(12, 16))
                (u(0) and 0xfe) == 0xfc -> true
                u(0) == 0xfe && (u(1) and 0xc0) == 0x80 -> true
                else -> false
            }
        }
        else -> false
    }
}
