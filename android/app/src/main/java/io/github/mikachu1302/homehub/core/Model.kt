package io.github.mikachu1302.homehub.core

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToLong

/** The app's sections. The menu is the start screen; the others open full screen. */
enum class Section { MENU, SWITCHES, AMP, TV, DEVICES }

/**
 * A Shelly the user has found. [address] is kept so the device also works through the VPN, where mDNS
 * names do not resolve. [names] holds the user's own name per channel.
 */
data class SavedDevice(
    val id: String,
    val address: String,
    val app: String,
    val gen: Int,
    val channels: Int,
    val names: Map<Int, String> = emptyMap(),
) {
    fun nameOf(channel: Int): String =
        names[channel]?.takeIf { it.isNotBlank() }
            ?: if (channels > 1) "$app · ${channel + 1}" else app
}

/** Timer presets offered in the editor, in seconds. */
val DELAY_PRESETS = listOf(60.0, 300.0, 420.0, 900.0, 1800.0, 3600.0, 7200.0)

/** "45 s", "7 min", "1 min 30 s", "2 h", "1 h 30 min" (spec/fixtures/delay-format.json). */
fun formatDelay(seconds: Double): String {
    val total = seconds.roundToLong().coerceAtLeast(0)
    if (total == 0L) return "0 s"
    val h = total / 3600
    val m = total % 3600 / 60
    val s = total % 60
    return listOfNotNull(
        h.takeIf { it > 0 }?.let { "$it h" },
        m.takeIf { it > 0 }?.let { "$it min" },
        s.takeIf { it > 0 }?.let { "$it s" },
    ).joinToString(" ")
}

/** "7.6 W"; whole numbers without decimals ("0 W"). */
fun formatWatts(watts: Double): String =
    if (watts == Math.floor(watts) && watts < 10_000) "${watts.toLong()} W" else "%.1f W".format(java.util.Locale.ROOT, watts)

/** Merges newly found devices into the saved list: keeps the user's names, updates address and channel count. */
fun mergeDevices(saved: List<SavedDevice>, found: List<SavedDevice>): List<SavedDevice> {
    val byId = saved.associateBy { it.id }.toMutableMap()
    for (f in found) {
        val old = byId[f.id]
        byId[f.id] = old?.copy(address = f.address, app = f.app, gen = f.gen, channels = f.channels) ?: f
    }
    return byId.values.sortedWith(compareBy({ it.app }, { it.id }))
}

object DeviceJson {
    fun encode(devices: List<SavedDevice>): String = JSONArray().apply {
        devices.forEach { d ->
            put(
                JSONObject()
                    .put("id", d.id)
                    .put("address", d.address)
                    .put("app", d.app)
                    .put("gen", d.gen)
                    .put("channels", d.channels)
                    .put("names", JSONObject().apply { d.names.forEach { (k, v) -> put(k.toString(), v) } }),
            )
        }
    }.toString()

    fun decode(text: String?): List<SavedDevice> {
        if (text.isNullOrBlank()) return emptyList()
        val arr = try {
            JSONArray(text)
        } catch (_: org.json.JSONException) {
            return emptyList()
        }
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val names = o.optJSONObject("names")
            SavedDevice(
                id = o.optString("id").ifEmpty { return@mapNotNull null },
                address = o.optString("address").ifEmpty { return@mapNotNull null },
                app = o.optString("app"),
                gen = o.optInt("gen", 2),
                channels = o.optInt("channels", 1),
                names = names?.keys()?.asSequence()
                    ?.mapNotNull { k -> k.toIntOrNull()?.let { it to names.optString(k) } }
                    ?.toMap() ?: emptyMap(),
            )
        }
    }
}
