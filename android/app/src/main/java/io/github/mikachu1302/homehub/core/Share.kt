package io.github.mikachu1302.homehub.core

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Everything two phones of the same household always share (docs/SHARING.md). A new household setting must be
 * added here, to [SetupShare] and to the doc.
 */
data class Household(
    val devices: List<SavedDevice>,
    val outputOrder: List<String>,
    val roomOrder: List<String>,
)

/**
 * How a person likes to use the app (docs/SHARING.md). Shared too, but the receiving phone chooses whether to take
 * them over. A new preference must be added here, to [SetupShare] and to the doc.
 */
data class Preferences(val switchView: SwitchView)

/** What a setup link carries: the household, and the sender's preferences (absent in links from older apps). */
data class SharedSetup(val household: Household, val preferences: Preferences?)

class ShareException(message: String) : Exception(message)

/**
 * The setup as a link for a QR code: `homehub://import?d=<data>`, where data is the JSON payload, raw-deflated and
 * base64url-encoded without padding. Format and rules: docs/SHARING.md; cases: spec/fixtures/setup-share.json.
 */
object SetupShare {
    /** Bump when the payload changes in a way an older app cannot read; older apps then refuse it clearly. */
    const val VERSION = 1
    const val LINK_PREFIX = "homehub://import?d="
    private const val MAX_PAYLOAD = 64 * 1024

    fun toJson(s: SharedSetup): String = JSONObject()
        .put("v", VERSION)
        .put("devices", JSONArray(DeviceJson.encode(s.household.devices)))
        .put("outputOrder", JSONArray(s.household.outputOrder))
        .put("roomOrder", JSONArray(s.household.roomOrder))
        .apply { s.preferences?.let { put("prefs", JSONObject().put("switchView", it.switchView.name)) } }
        .toString()

    fun fromJson(text: String): SharedSetup {
        val o = try {
            JSONObject(text)
        } catch (_: JSONException) {
            throw ShareException("This is not a Home Hub setup.")
        }
        val v = o.optInt("v", 0)
        if (v < 1) throw ShareException("This is not a Home Hub setup.")
        if (v > VERSION) throw ShareException("This setup comes from a newer Home Hub. Update the app first.")
        val devices = DeviceJson.decode(o.optJSONArray("devices")?.toString())
        val household = Household(devices, strings(o.optJSONArray("outputOrder")), strings(o.optJSONArray("roomOrder")))
        // Unknown or missing values fall back to the default, so a newer sender's extra preferences do no harm.
        val prefs = o.optJSONObject("prefs")?.let { p ->
            Preferences(
                switchView = runCatching { SwitchView.valueOf(p.optString("switchView")) }.getOrDefault(SwitchView.ROOMS),
            )
        }
        return SharedSetup(household, prefs)
    }

    fun toLink(s: SharedSetup): String = LINK_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(deflate(toJson(s).toByteArray()))

    /** Accepts the full link (also with spaces or line breaks around it, as pasted from a messenger). */
    fun fromLink(link: String): SharedSetup {
        val trimmed = link.trim()
        if (!trimmed.startsWith(LINK_PREFIX)) throw ShareException("This is not a Home Hub setup link.")
        val data = trimmed.removePrefix(LINK_PREFIX).substringBefore('&')
        val bytes = try {
            Base64.getUrlDecoder().decode(data)
        } catch (_: IllegalArgumentException) {
            throw ShareException("The setup link is damaged.")
        }
        return fromJson(String(inflate(bytes)))
    }

    /**
     * Applies a received setup: devices are matched by id and take the shared version (address, outputs with name,
     * room, icon); devices only on this phone stay. The shared orders come first, local-only entries keep theirs after.
     */
    fun merge(local: Household, shared: Household): Household {
        val sharedIds = shared.devices.map { it.id }.toSet()
        val devices = (shared.devices + local.devices.filter { it.id !in sharedIds })
            .sortedWith(compareBy({ it.app }, { it.id }))
        return Household(
            devices = devices,
            outputOrder = shared.outputOrder + local.outputOrder.filter { it !in shared.outputOrder },
            roomOrder = shared.roomOrder + local.roomOrder.filter { r -> shared.roomOrder.none { it.equals(r, ignoreCase = true) } },
        )
    }

    private fun strings(a: JSONArray?): List<String> = if (a == null) emptyList() else (0 until a.length()).map { a.optString(it) }

    private fun deflate(input: ByteArray): ByteArray {
        val d = Deflater(Deflater.BEST_COMPRESSION, true)
        d.setInput(input)
        d.finish()
        val out = ByteArrayOutputStream()
        val buf = ByteArray(1024)
        while (!d.finished()) out.write(buf, 0, d.deflate(buf))
        d.end()
        return out.toByteArray()
    }

    private fun inflate(input: ByteArray): ByteArray {
        // "nowrap" inflation needs one extra dummy byte after the data.
        val inf = Inflater(true)
        inf.setInput(input + 0)
        val out = ByteArrayOutputStream()
        val buf = ByteArray(1024)
        try {
            while (!inf.finished()) {
                val n = inf.inflate(buf)
                if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break
                out.write(buf, 0, n)
                if (out.size() > MAX_PAYLOAD) throw ShareException("The setup link is too large.")
            }
        } catch (_: DataFormatException) {
            throw ShareException("The setup link is damaged.")
        } finally {
            inf.end()
        }
        return out.toByteArray()
    }
}
