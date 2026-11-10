package io.github.mikachu1302.homehub.net

import android.content.Context
import android.net.ConnectivityManager
import io.github.mikachu1302.homehub.core.SavedDevice
import io.github.mikachu1302.homehub.core.isLanAddress
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.Inet4Address

/**
 * Finds Shelly devices by asking every address of the phone's /24 for `GET /shelly`. Runs only when the user
 * taps "Find devices" (AGENTS.md) and only works at home: a VPN does not carry this. mDNS (`_shelly._tcp`)
 * would be quicker; the probe found every device on the real network, so it is the one method for now.
 */
suspend fun findShellys(context: Context): List<SavedDevice> {
    val own = ownIpv4(context) ?: return emptyList()
    val prefix = own.address.let { "${it[0].toInt() and 0xff}.${it[1].toInt() and 0xff}.${it[2].toInt() and 0xff}." }
    val client = ShellyClient(LanHttp(timeoutMs = 1500))
    val gate = Semaphore(48)
    return coroutineScope {
        (1..254).map { "$prefix$it" }
            .filter { it != own.hostAddress }
            .map { address ->
                async {
                    gate.withPermit {
                        try {
                            val info = client.identify(address) ?: return@withPermit null
                            val channels = client.switches(address).size
                            if (channels == 0) return@withPermit null
                            SavedDevice(id = info.id, address = address, app = info.app, gen = info.gen, channels = channels)
                        } catch (_: Exception) {
                            null
                        }
                    }
                }
            }
            .awaitAll()
            .filterNotNull()
    }
}

/** The phone's private IPv4 address on the active network (Wi-Fi at home), or null. */
private fun ownIpv4(context: Context): Inet4Address? {
    val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
    val props = cm.getLinkProperties(cm.activeNetwork) ?: return null
    return props.linkAddresses.map { it.address }
        .filterIsInstance<Inet4Address>()
        .firstOrNull { isLanAddress(it.address) && !it.isLoopbackAddress }
}
