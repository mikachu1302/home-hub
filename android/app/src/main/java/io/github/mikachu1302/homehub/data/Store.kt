package io.github.mikachu1302.homehub.data

import android.content.Context
import androidx.core.content.edit
import io.github.mikachu1302.homehub.core.DeviceJson
import io.github.mikachu1302.homehub.core.Household
import io.github.mikachu1302.homehub.core.SavedDevice
import io.github.mikachu1302.homehub.core.Section
import io.github.mikachu1302.homehub.core.SwitchView
import org.json.JSONArray

/**
 * Every stored setting, each classified as household (shared with the other phone by QR code) or personal
 * (this phone only). Rules: docs/SHARING.md. A new key must go into exactly one of the two sets;
 * SettingKeysTest fails otherwise. A new household key must also go into [Household] and SetupShare.
 */
object SettingKeys {
    const val DEVICES = "devices"
    const val OUTPUT_ORDER = "output_order"
    const val ROOM_ORDER = "room_order"
    const val LAST_SECTION = "last_section"
    const val BACK_HINT = "back_hint_shown"
    const val SWITCH_VIEW = "switch_view"

    val HOUSEHOLD = setOf(DEVICES, OUTPUT_ORDER, ROOM_ORDER)
    val PERSONAL = setOf(LAST_SECTION, BACK_HINT, SWITCH_VIEW)
}

/** Device list and settings (SharedPreferences, no backup). No secrets here: credentials, once needed, go to the Keystore. */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("home-hub", Context.MODE_PRIVATE)

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

    /** All household settings at once, for sharing and importing. */
    var household: Household
        get() = Household(devices, outputOrder, roomOrder)
        set(value) {
            devices = value.devices
            outputOrder = value.outputOrder
            roomOrder = value.roomOrder
        }

    // --- personal (this phone only) ---

    /** The page the app reopens on (the last section used; Devices counts as the menu). */
    var lastSection: Section
        get() = prefs.getString(SettingKeys.LAST_SECTION, null)?.let { runCatching { Section.valueOf(it) }.getOrNull() } ?: Section.MENU
        set(value) = prefs.edit { putString(SettingKeys.LAST_SECTION, (if (value == Section.DEVICES) Section.MENU else value).name) }

    /** How the Switches page lists outputs. */
    var switchView: SwitchView
        get() = prefs.getString(SettingKeys.SWITCH_VIEW, null)?.let { runCatching { SwitchView.valueOf(it) }.getOrNull() } ?: SwitchView.ROOMS
        set(value) = prefs.edit { putString(SettingKeys.SWITCH_VIEW, value.name) }

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
