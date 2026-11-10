package io.github.mikachu1302.homehub.data

import android.content.Context
import androidx.core.content.edit
import io.github.mikachu1302.homehub.core.DeviceJson
import io.github.mikachu1302.homehub.core.SavedDevice
import io.github.mikachu1302.homehub.core.Section
import io.github.mikachu1302.homehub.core.SwitchView

/** Device list and small UI settings. No secrets here: credentials, once needed, go to the Keystore. */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("home-hub", Context.MODE_PRIVATE)

    var devices: List<SavedDevice>
        get() = DeviceJson.decode(prefs.getString(KEY_DEVICES, null))
        set(value) = prefs.edit { putString(KEY_DEVICES, DeviceJson.encode(value)) }

    /** The page the app reopens on (the last section used; Devices counts as the menu). */
    var lastSection: Section
        get() = prefs.getString(KEY_SECTION, null)?.let { runCatching { Section.valueOf(it) }.getOrNull() } ?: Section.MENU
        set(value) = prefs.edit { putString(KEY_SECTION, (if (value == Section.DEVICES) Section.MENU else value).name) }

    /** How the Switches page lists outputs; a personal setting of this phone. */
    var switchView: SwitchView
        get() = prefs.getString(KEY_VIEW, null)?.let { runCatching { SwitchView.valueOf(it) }.getOrNull() } ?: SwitchView.ROOMS
        set(value) = prefs.edit { putString(KEY_VIEW, value.name) }

    /** The order of the outputs (keys from `outputKey`) and of the rooms: household data, shared later. */
    var outputOrder: List<String>
        get() = readList(KEY_OUTPUT_ORDER)
        set(value) = writeList(KEY_OUTPUT_ORDER, value)

    var roomOrder: List<String>
        get() = readList(KEY_ROOM_ORDER)
        set(value) = writeList(KEY_ROOM_ORDER, value)

    private fun readList(key: String): List<String> = runCatching {
        val a = org.json.JSONArray(prefs.getString(key, "[]"))
        (0 until a.length()).map { a.getString(it) }
    }.getOrDefault(emptyList())

    private fun writeList(key: String, value: List<String>) = prefs.edit { putString(key, org.json.JSONArray(value).toString()) }

    /** Whether the one-time "swipe back to the menu" hint was shown. */
    var backHintShown: Boolean
        get() = prefs.getBoolean(KEY_HINT, false)
        set(value) = prefs.edit { putBoolean(KEY_HINT, value) }

    private companion object {
        const val KEY_DEVICES = "devices"
        const val KEY_SECTION = "last_section"
        const val KEY_HINT = "back_hint_shown"
        const val KEY_VIEW = "switch_view"
        const val KEY_OUTPUT_ORDER = "output_order"
        const val KEY_ROOM_ORDER = "room_order"
    }
}
