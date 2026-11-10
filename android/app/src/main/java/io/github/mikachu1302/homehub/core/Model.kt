package io.github.mikachu1302.homehub.core

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToLong

/** The app's sections. The menu is the start screen; the others open full screen. */
enum class Section { MENU, SWITCHES, AMP, TV, DEVICES }

/** How the Switches page lists the outputs. */
enum class SwitchView { ROOMS, ALL }

/** The user's settings for one output (channel). Blank means "not set". */
data class OutputPrefs(val name: String = "", val room: String = "")

/**
 * A Shelly the user has found. [address] is kept so the device also works through the VPN, where mDNS
 * names do not resolve. [outputs] holds the user's settings per channel.
 */
data class SavedDevice(
    val id: String,
    val address: String,
    val app: String,
    val gen: Int,
    val channels: Int,
    val outputs: Map<Int, OutputPrefs> = emptyMap(),
) {
    fun prefsOf(channel: Int): OutputPrefs = outputs[channel] ?: OutputPrefs()

    fun nameOf(channel: Int): String =
        prefsOf(channel).name.trim().takeIf { it.isNotEmpty() }
            ?: if (channels > 1) "$app · ${channel + 1}" else app

    fun roomOf(channel: Int): String = prefsOf(channel).room.trim()

    fun withPrefs(channel: Int, prefs: OutputPrefs): SavedDevice = copy(outputs = outputs + (channel to prefs))
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

/** Merges newly found devices into the saved list: keeps the user's settings, updates address and channel count. */
fun mergeDevices(saved: List<SavedDevice>, found: List<SavedDevice>): List<SavedDevice> {
    val byId = saved.associateBy { it.id }.toMutableMap()
    for (f in found) {
        val old = byId[f.id]
        byId[f.id] = old?.copy(address = f.address, app = f.app, gen = f.gen, channels = f.channels) ?: f
    }
    return byId.values.sortedWith(compareBy({ it.app }, { it.id }))
}

/** Every room name in use, without duplicates (case-insensitive, first spelling wins), sorted. */
fun roomsInUse(devices: List<SavedDevice>): List<String> =
    devices.flatMap { d -> (0 until d.channels).map { d.roomOf(it) } }
        .filter { it.isNotEmpty() }
        .distinctBy { it.lowercase() }
        .sortedBy { it.lowercase() }

/** A room heading with its items; [room] is null for the "Other" group (no room set). */
data class RoomGroup<T>(val room: String?, val items: List<T>)

/**
 * Groups [items] by room: rooms alphabetically (case-insensitive, the same room however it is capitalised),
 * items keep their order inside a room, items without a room last (spec/fixtures/room-grouping.json).
 */
fun <T> groupByRoom(items: List<T>, roomOf: (T) -> String): List<RoomGroup<T>> {
    val withRoom = items.filter { roomOf(it).trim().isNotEmpty() }
    val groups = withRoom.groupBy { roomOf(it).trim().lowercase() }
        .map { (_, list) -> RoomGroup<T>(roomOf(list.first()).trim(), list) }
        .sortedBy { it.room!!.lowercase() }
    val rest = items.filter { roomOf(it).trim().isEmpty() }
    return if (rest.isEmpty()) groups else groups + RoomGroup(null, rest)
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
                    .put(
                        "outputs",
                        JSONObject().apply {
                            d.outputs.forEach { (ch, p) -> put(ch.toString(), JSONObject().put("name", p.name).put("room", p.room)) }
                        },
                    ),
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
            SavedDevice(
                id = o.optString("id").ifEmpty { return@mapNotNull null },
                address = o.optString("address").ifEmpty { return@mapNotNull null },
                app = o.optString("app"),
                gen = o.optInt("gen", 2),
                channels = o.optInt("channels", 1),
                outputs = decodeOutputs(o),
            )
        }
    }

    private fun decodeOutputs(o: JSONObject): Map<Int, OutputPrefs> {
        o.optJSONObject("outputs")?.let { outs ->
            return outs.keys().asSequence().mapNotNull { k ->
                val ch = k.toIntOrNull() ?: return@mapNotNull null
                val p = outs.optJSONObject(k) ?: return@mapNotNull null
                ch to OutputPrefs(p.optString("name"), p.optString("room"))
            }.toMap()
        }
        // First version stored only names: {"names": {"0": "Lamp"}}.
        val names = o.optJSONObject("names") ?: return emptyMap()
        return names.keys().asSequence()
            .mapNotNull { k -> k.toIntOrNull()?.let { it to OutputPrefs(name = names.optString(k)) } }
            .toMap()
    }
}
