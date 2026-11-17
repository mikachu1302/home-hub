package io.github.mikachu1302.homehub.data

import android.content.Context
import androidx.core.content.edit
import io.github.mikachu1302.homehub.core.DeviceJson
import io.github.mikachu1302.homehub.core.Household
import io.github.mikachu1302.homehub.core.Appearance
import io.github.mikachu1302.homehub.core.CustomPage
import io.github.mikachu1302.homehub.core.PagesJson
import io.github.mikachu1302.homehub.core.SwitchLayout
import io.github.mikachu1302.homehub.core.Preferences
import io.github.mikachu1302.homehub.core.UiStyle
import io.github.mikachu1302.homehub.core.SavedDevice
import io.github.mikachu1302.homehub.core.Section
import io.github.mikachu1302.homehub.core.SwitchView
import io.github.mikachu1302.homehub.core.AppLink
import io.github.mikachu1302.homehub.core.RemoteBehaviour
import io.github.mikachu1302.homehub.core.VolumeTv
import io.github.mikachu1302.homehub.tv.AppShortcut
import io.github.mikachu1302.homehub.tv.AudioTv
import io.github.mikachu1302.homehub.tv.RemoteSettings
import io.github.mikachu1302.homehub.tv.appsToJson
import io.github.mikachu1302.homehub.tv.audioTvToJson
import io.github.mikachu1302.homehub.tv.loadSettings
import io.github.mikachu1302.homehub.tv.parseApps
import io.github.mikachu1302.homehub.tv.parseAudioTv
import io.github.mikachu1302.homehub.tv.putSettings
import org.json.JSONArray

/**
 * Every stored setting, classified (rules: docs/SHARING.md):
 * - HOUSEHOLD: always shared by the setup link ([Household]);
 * - PREFERENCES: shared too, the receiving phone chooses whether to take them over ([Preferences]);
 * - STATE: app state of this phone, never shared (which page was open, hints already shown).
 * A new key must go into exactly one set; ShareTest fails otherwise. Credentials are none of these: they live in
 * the Keystore ([SecretStore]) and are only shared behind an explicit "Include passwords" switch.
 */
object SettingKeys {
    const val DEVICES = "devices"
    const val OUTPUT_ORDER = "output_order"
    const val ROOM_ORDER = "room_order"
    const val IR_BRIDGE = "ir_bridge"
    const val LAST_SECTION = "last_section"
    const val BACK_HINT = "back_hint_shown"
    const val SWITCH_VIEW = "switch_view"
    const val MENU_ORDER = "menu_order"
    const val MENU_NAMES = "menu_names"
    const val STYLE = "style"
    const val PALETTE = "palette"
    const val SCANLINES = "scanlines"
    const val PAGES = "pages"
    const val SWITCH_LAYOUT = "switch_layout"
    const val LAST_PAGE = "last_page"

    // The Android TV and TV settings, ported from tv-remote, live in their own preferences file "tv"
    // (tv/RemoteViewModel.kt, tv/Prefs.kt); listed here as "tv/<key>" so they are classified too. Shared since
    // 2026-11-17: the TV for volume (household), the remote behaviour and app shortcuts (preferences). The pairing
    // stays on this phone by design: its key never leaves the Keystore (docs/SHARING.md).
    const val TV_PAIRED = "tv/tvs"
    const val TV_ACTIVE = "tv/activeTv"
    const val TV_APPS = "tv/apps"
    const val TV_AUDIO = "tv/audioTv"
    const val TV_HAPTICS = "tv/haptics"
    const val TV_SMOOTH_PRESS = "tv/smoothPress"
    const val TV_KEEP_AWAKE = "tv/keepAwake"
    const val TV_THEATER = "tv/theater"
    const val TV_VOLUME = "tv/tvVolume"

    val HOUSEHOLD = setOf(DEVICES, OUTPUT_ORDER, ROOM_ORDER, IR_BRIDGE, TV_AUDIO)
    val PREFERENCES = setOf(
        SWITCH_VIEW, MENU_ORDER, MENU_NAMES, STYLE, PALETTE, SCANLINES, PAGES, SWITCH_LAYOUT,
        TV_APPS, TV_HAPTICS, TV_SMOOTH_PRESS, TV_KEEP_AWAKE, TV_THEATER, TV_VOLUME,
    )
    val STATE = setOf(LAST_SECTION, BACK_HINT, LAST_PAGE, TV_PAIRED, TV_ACTIVE)

    /** A key in the "tv" file, without the "tv/" prefix used for classifying it. */
    fun inTvFile(key: String) = key.removePrefix("tv/")
}

/** Device list and settings (SharedPreferences, no backup). No secrets here: credentials go to [SecretStore]. */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("home-hub", Context.MODE_PRIVATE)
    /** tv-remote's file (tv/RemoteViewModel.kt): read and written here only for sharing; that view model reloads after an import. */
    private val tv = context.getSharedPreferences("tv", Context.MODE_PRIVATE)

    // --- household (shared) ---

    var devices: List<SavedDevice>
        get() = DeviceJson.decode(prefs.getString(SettingKeys.DEVICES, null))
        set(value) = prefs.edit { putString(SettingKeys.DEVICES, DeviceJson.encode(value)) }

    /** The order of the outputs (keys from `outputKey`) and of the rooms. */
    var outputOrder: List<String>
        get() = readList(SettingKeys.OUTPUT_ORDER)
        set(value) = writeList(SettingKeys.OUTPUT_ORDER, value)

    var roomOrder: List<String>
        get() = readList(SettingKeys.ROOM_ORDER)
        set(value) = writeList(SettingKeys.ROOM_ORDER, value)

    /** The IR bridge's address (IP or host name); blank = not set up. Its login is in [SecretStore]. */
    var irBridge: String
        get() = prefs.getString(SettingKeys.IR_BRIDGE, "") ?: ""
        set(value) = prefs.edit { putString(SettingKeys.IR_BRIDGE, value) }

    /** The TV for volume and Wake-on-LAN (Settings > TV); null = not set up. */
    var volumeTv: VolumeTv?
        get() = parseAudioTv(tv.getString(SettingKeys.inTvFile(SettingKeys.TV_AUDIO), null))
            ?.let { VolumeTv(it.name, it.host, it.port, it.control, it.mac) }
        set(value) = tv.edit {
            val key = SettingKeys.inTvFile(SettingKeys.TV_AUDIO)
            if (value == null) remove(key) else putString(key, audioTvToJson(AudioTv(value.name, value.host, value.port, value.control, value.mac)))
        }

    /** All household settings at once, for sharing and importing. */
    var household: Household
        get() = Household(devices, outputOrder, roomOrder, irBridge, volumeTv)
        set(value) {
            devices = value.devices
            outputOrder = value.outputOrder
            roomOrder = value.roomOrder
            irBridge = value.irBridge
            value.tv?.let { volumeTv = it } // none shared: keep this phone's
        }

    // --- preferences (shared, taken over on request) ---

    /** How the Switches page lists outputs. */
    var switchView: SwitchView
        get() = prefs.getString(SettingKeys.SWITCH_VIEW, null)?.let { runCatching { SwitchView.valueOf(it) }.getOrNull() } ?: SwitchView.ROOMS
        set(value) = prefs.edit { putString(SettingKeys.SWITCH_VIEW, value.name) }

    /** Menu entries in the user's order (`Section` names and `page:<id>`), and the user's titles for them. */
    var menuOrder: List<String>
        get() = readList(SettingKeys.MENU_ORDER)
        set(value) = writeList(SettingKeys.MENU_ORDER, value)

    var menuNames: Map<String, String>
        get() = runCatching {
            val o = org.json.JSONObject(prefs.getString(SettingKeys.MENU_NAMES, "{}") ?: "{}")
            o.keys().asSequence().associateWith { o.optString(it) }
        }.getOrDefault(emptyMap())
        set(value) = prefs.edit { putString(SettingKeys.MENU_NAMES, org.json.JSONObject(value).toString()) }

    /** Look and colours. */
    var appearance: Appearance
        get() {
            val d = Appearance()
            return Appearance(
                style = UiStyle.entries.firstOrNull { it.name == prefs.getString(SettingKeys.STYLE, null) } ?: d.style,
                palette = prefs.getString(SettingKeys.PALETTE, null) ?: d.palette,
                scanlines = prefs.getBoolean(SettingKeys.SCANLINES, d.scanlines),
            )
        }
        set(value) = prefs.edit {
            putString(SettingKeys.STYLE, value.style.name)
            putString(SettingKeys.PALETTE, value.palette)
            putBoolean(SettingKeys.SCANLINES, value.scanlines)
        }

    var switchLayout: SwitchLayout
        get() = prefs.getString(SettingKeys.SWITCH_LAYOUT, null)?.let { runCatching { SwitchLayout.valueOf(it) }.getOrNull() } ?: SwitchLayout.LIST
        set(value) = prefs.edit { putString(SettingKeys.SWITCH_LAYOUT, value.name) }

    /** The user's own pages. */
    var pages: List<CustomPage>
        get() = PagesJson.decode(prefs.getString(SettingKeys.PAGES, null))
        set(value) = prefs.edit { putString(SettingKeys.PAGES, PagesJson.encode(value)) }

    /** Settings > Remote. */
    var remote: RemoteBehaviour
        get() = tv.loadSettings().let { RemoteBehaviour(it.haptics, it.smoothPress, it.keepAwake, it.theater, it.tvVolume) }
        set(value) = tv.edit { putSettings(RemoteSettings(value.haptics, value.smoothPress, value.keepAwake, value.theater, value.tvVolume)) }

    /** The Android TV page's app shortcuts (tv-remote's defaults until the user changes them). */
    var apps: List<AppLink>
        get() = parseApps(tv.getString(SettingKeys.inTvFile(SettingKeys.TV_APPS), null)).map { AppLink(it.name, it.link) }
        set(value) = tv.edit { putString(SettingKeys.inTvFile(SettingKeys.TV_APPS), appsToJson(value.map { AppShortcut(it.name, it.link) })) }

    var preferences: Preferences
        get() = Preferences(switchView, menuOrder, menuNames, appearance, pages, switchLayout, remote, apps)
        set(value) {
            switchView = value.switchView
            menuOrder = value.menuOrder
            menuNames = value.menuNames
            appearance = value.appearance
            value.pages?.let { pages = it } // null: keep this phone's pages
            switchLayout = value.switchLayout
            value.remote?.let { remote = it } // null (an older link): keep this phone's
            value.apps?.let { apps = it }
        }

    // --- state (this phone only, never shared) ---

    /** The custom page the app reopens on, when [lastSection] is a page. */
    var lastPage: String
        get() = prefs.getString(SettingKeys.LAST_PAGE, "") ?: ""
        set(value) = prefs.edit { putString(SettingKeys.LAST_PAGE, value) }

    /** The page the app reopens on (the last section used; Settings counts as the menu). */
    var lastSection: Section
        get() = prefs.getString(SettingKeys.LAST_SECTION, null)?.let { runCatching { Section.valueOf(it) }.getOrNull() } ?: Section.MENU
        set(value) = prefs.edit { putString(SettingKeys.LAST_SECTION, (if (value == Section.SETTINGS) Section.MENU else value).name) }

    /** Whether the one-time "swipe back to the menu" hint was shown. */
    var backHintShown: Boolean
        get() = prefs.getBoolean(SettingKeys.BACK_HINT, false)
        set(value) = prefs.edit { putBoolean(SettingKeys.BACK_HINT, value) }

    private fun readList(key: String): List<String> = runCatching {
        val a = JSONArray(prefs.getString(key, "[]"))
        (0 until a.length()).map { a.getString(it) }
    }.getOrDefault(emptyList())

    private fun writeList(key: String, value: List<String>) = prefs.edit { putString(key, JSONArray(value).toString()) }
}
