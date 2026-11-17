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
    /** The IR bridge's address; blank = not set up. */
    val irBridge: String = "",
    /** The TV for volume and Wake-on-LAN (Settings > TV); null = not set up (an import then keeps the receiver's). */
    val tv: VolumeTv? = null,
)

/**
 * The TV whose own volume the volume keys control (UPnP RenderingControl at [host]:[port][control]) and that
 * Wake-on-LAN wakes ([mac]); docs/PROTOCOLS.md. Household: it is the same TV for everyone at home.
 */
data class VolumeTv(val name: String, val host: String, val port: Int, val control: String, val mac: String? = null)

/** How the remote keys behave (Settings > Remote): a preference. */
data class RemoteBehaviour(
    val haptics: Boolean = true,
    val smoothPress: Boolean = true,
    val keepAwake: Boolean = true,
    val theater: Boolean = false,
    /** The volume keys control the TV itself (when one is set up) rather than the stick. */
    val tvVolume: Boolean = true,
)

/** A tile in the Android TV page's app row: [link] opens the app on the stick (docs/PROTOCOLS.md). */
data class AppLink(val name: String, val link: String)

/**
 * How a person likes to use the app (docs/SHARING.md). Shared too, but the receiving phone chooses whether to take
 * them over. A new preference must be added here, to [SetupShare] and to the doc.
 */
data class Preferences(
    val switchView: SwitchView,
    /** Menu entries in the user's order: `Section` names and custom pages as `page:<id>`. Empty = default order. */
    val menuOrder: List<String> = emptyList(),
    /** The user's titles for menu sections, keyed by `Section` name. Missing = default title. */
    val menuNames: Map<String, String> = emptyMap(),
    /** Look and colours. */
    val appearance: Appearance = Appearance(),
    /**
     * The user's own pages (custom pages; their place in the menu is in [menuOrder]). Null: the payload has none
     * (a link from before custom pages), so the receiver keeps its own pages (docs/SHARING.md).
     */
    val pages: List<CustomPage>? = emptyList(),
    /** Rows or compact cards on the Switches page. */
    val switchLayout: SwitchLayout = SwitchLayout.LIST,
    /** Settings > Remote; null: not in the payload (a link from before it was shared), the receiver keeps its own. */
    val remote: RemoteBehaviour? = null,
    /** The Android TV page's app shortcuts, in order; null: not in the payload, the receiver keeps its own. */
    val apps: List<AppLink>? = null,
)

/**
 * What a setup link carries. The sender chooses the parts, so either may be absent (but not both); links from
 * the first app version always carry the household and never preferences.
 */
data class SharedSetup(val household: Household?, val preferences: Preferences?, val secrets: Secrets? = null)

/** Passwords, only in a link when the sender switched on "Include passwords" for it (docs/SHARING.md). */
data class Secrets(val irBridge: BridgeLogin? = null)

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
        .apply {
            s.household?.let { h ->
                put("devices", JSONArray(DeviceJson.encode(h.devices)))
                put("outputOrder", JSONArray(h.outputOrder))
                put("roomOrder", JSONArray(h.roomOrder))
                if (h.irBridge.isNotBlank()) put("irBridge", h.irBridge)
                h.tv?.let { tv ->
                    put("tv", JSONObject().put("name", tv.name).put("host", tv.host).put("port", tv.port).put("control", tv.control)
                        .apply { tv.mac?.let { put("mac", it) } })
                }
            }
            s.preferences?.let { p ->
                put(
                    "prefs",
                    JSONObject()
                        .put("switchView", p.switchView.name)
                        .put("menuOrder", JSONArray(p.menuOrder))
                        .put("menuNames", JSONObject(p.menuNames))
                        .put("style", p.appearance.style.name)
                        .put("palette", p.appearance.palette)
                        .put("scanlines", p.appearance.scanlines)
                        .put("pages", PagesJson.toJson(p.pages.orEmpty()))
                        .put("switchLayout", p.switchLayout.name)
                        .apply {
                            p.remote?.let { r ->
                                put("remote", JSONObject().put("haptics", r.haptics).put("smoothPress", r.smoothPress)
                                    .put("keepAwake", r.keepAwake).put("theater", r.theater).put("tvVolume", r.tvVolume))
                            }
                            p.apps?.let { a -> put("apps", JSONArray(a.map { JSONObject().put("name", it.name).put("link", it.link) })) }
                        },
                )
            }
            s.secrets?.let { x ->
                put("secrets", JSONObject().apply {
                    x.irBridge?.let { put("irBridge", JSONObject().put("user", it.user).put("password", it.password)) }
                })
            }
        }
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
        // The household part is present exactly when "devices" is (the sender may leave it out).
        val household = if (!o.has("devices")) null else Household(
            DeviceJson.decode(o.optJSONArray("devices")?.toString()),
            strings(o.optJSONArray("outputOrder")),
            strings(o.optJSONArray("roomOrder")),
            o.optString("irBridge").trim(),
            o.optJSONObject("tv")?.let(::volumeTv),
        )
        // Unknown or missing values fall back to the default, so a newer sender's extra preferences do no harm.
        val prefs = o.optJSONObject("prefs")?.let { p ->
            Preferences(
                switchView = runCatching { SwitchView.valueOf(p.optString("switchView")) }.getOrDefault(SwitchView.ROOMS),
                menuOrder = strings(p.optJSONArray("menuOrder")),
                menuNames = p.optJSONObject("menuNames")?.let { n -> n.keys().asSequence().associateWith { n.optString(it) } } ?: emptyMap(),
                appearance = Appearance(
                    style = UiStyle.entries.firstOrNull { it.name == p.optString("style") } ?: Appearance().style,
                    palette = p.optString("palette").ifBlank { Appearance().palette },
                    scanlines = p.optBoolean("scanlines", Appearance().scanlines),
                ),
                pages = if (p.has("pages")) PagesJson.fromJson(p.optJSONArray("pages")) else null,
                switchLayout = runCatching { SwitchLayout.valueOf(p.optString("switchLayout")) }.getOrDefault(SwitchLayout.LIST),
                remote = p.optJSONObject("remote")?.let { r ->
                    val d = RemoteBehaviour()
                    RemoteBehaviour(
                        haptics = r.optBoolean("haptics", d.haptics),
                        smoothPress = r.optBoolean("smoothPress", d.smoothPress),
                        keepAwake = r.optBoolean("keepAwake", d.keepAwake),
                        theater = r.optBoolean("theater", d.theater),
                        tvVolume = r.optBoolean("tvVolume", d.tvVolume),
                    )
                },
                apps = p.optJSONArray("apps")?.let { a ->
                    (0 until a.length()).mapNotNull { i ->
                        val x = a.optJSONObject(i) ?: return@mapNotNull null
                        val name = x.optString("name").trim()
                        val link = x.optString("link").trim()
                        if (name.isEmpty() || link.isEmpty()) null else AppLink(name, link)
                    }
                },
            )
        }
        val secrets = o.optJSONObject("secrets")?.let { x ->
            Secrets(
                irBridge = x.optJSONObject("irBridge")
                    ?.let { BridgeLogin(it.optString("user"), it.optString("password")) }
                    ?.takeIf { it.user.isNotEmpty() },
            )
        }?.takeIf { it.irBridge != null }
        if (household == null && prefs == null && secrets == null) throw ShareException("This setup is empty.")
        return SharedSetup(household, prefs, secrets)
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
 * A shared bridge address replaces the local one; none shared keeps it. The same for the TV for volume.
     */
    fun merge(local: Household, shared: Household): Household {
        val sharedIds = shared.devices.map { it.id }.toSet()
        val devices = (shared.devices + local.devices.filter { it.id !in sharedIds })
            .sortedWith(compareBy({ it.app }, { it.id }))
        return Household(
            devices = devices,
            outputOrder = shared.outputOrder + local.outputOrder.filter { it !in shared.outputOrder },
            roomOrder = shared.roomOrder + local.roomOrder.filter { r -> shared.roomOrder.none { it.equals(r, ignoreCase = true) } },
            irBridge = shared.irBridge.ifBlank { local.irBridge },
            tv = shared.tv ?: local.tv,
        )
    }

    /** A TV without an address, port or control path is useless and dropped; a blank MAC is none. */
    private fun volumeTv(t: JSONObject): VolumeTv? {
        val host = t.optString("host").trim()
        val port = t.optInt("port", 0)
        val control = t.optString("control").trim()
        if (host.isEmpty() || port !in 1..65535 || control.isEmpty()) return null
        return VolumeTv(t.optString("name").trim().ifEmpty { host }, host, port, control, t.optString("mac").trim().ifEmpty { null })
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
