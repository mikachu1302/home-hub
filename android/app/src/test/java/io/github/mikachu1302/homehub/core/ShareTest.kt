package io.github.mikachu1302.homehub.core

import io.github.mikachu1302.homehub.data.SettingKeys
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier

/** Sharing the setup (docs/SHARING.md). Made-up devices and addresses throughout. */
class ShareTest {
    private val lamp = SavedDevice("shellyplus2pm-a0b1c2d3e4f5", "192.0.2.10", "Plus2PM", 2, 2)
        .withPrefs(0, OutputPrefs("Lamp", "Living room", "lamp"))
        .withPrefs(1, OutputPrefs("Strip", "Living room", "strip"))
    private val ceiling = SavedDevice("shellyplus1pm-112233445566", "192.0.2.11", "Plus1PM", 2, 1)
        .withPrefs(0, OutputPrefs("Ceiling", "Aisle", "ceiling"))

    @Test
    fun fixtureLinks() {
        val dir = System.getProperty("fixtures") ?: error("fixtures system property not set")
        val cases = JSONObject(File(dir, "setup-share.json").readText()).getJSONArray("cases")
        for (i in 0 until cases.length()) {
            val c = cases.getJSONObject(i)
            val name = c.getString("name")
            val e = c.getJSONObject("expected")
            if (e.has("household")) {
                val got = SetupShare.fromLink(c.getString("input"))
                val want = SetupShare.fromJson(e.getJSONObject("household").toString()).household
                assertEquals(name, want, got.household)
                assertEquals(name, listOf(ceiling, lamp).sortedBy { it.id }, got.household.devices.sortedBy { it.id })
                val prefs = if (e.isNull("preferences")) null else Preferences(SwitchView.valueOf(e.getJSONObject("preferences").getString("switchView")))
                assertEquals(name, prefs, got.preferences)
            } else {
                try {
                    SetupShare.fromLink(c.getString("input"))
                    fail("$name: expected an error")
                } catch (ex: ShareException) {
                    assertEquals(name, e.getString("error"), ex.message)
                }
            }
        }
    }

    @Test
    fun roundTrip() {
        val h = Household(listOf(lamp, ceiling), listOf(outputKey(ceiling.id, 0), outputKey(lamp.id, 1)), listOf("Aisle"))
        val setup = SharedSetup(h, Preferences(SwitchView.ALL))
        val link = SetupShare.toLink(setup)
        assertTrue(link.startsWith(SetupShare.LINK_PREFIX))
        assertEquals(setup, SetupShare.fromLink(link))
        assertEquals(SharedSetup(h, null), SetupShare.fromLink(SetupShare.toLink(SharedSetup(h, null))))
    }

    @Test
    fun sixOutputsFitEasilyIntoOneQrCode() {
        val devices = (1..5).map { n ->
            SavedDevice("shellyplus1pm-00000000000$n", "192.0.2.$n", "Plus1PM", 2, 1)
                .withPrefs(0, OutputPrefs("Some longer output name $n", "A room with a long name", "ceiling"))
        }
        val link = SetupShare.toLink(
            SharedSetup(Household(devices, devices.map { outputKey(it.id, 0) }, listOf("A room with a long name")), Preferences(SwitchView.ROOMS)),
        )
        // A QR code at error correction M holds 2331 bytes; stay far below so the code stays easy to scan.
        assertTrue("link is ${link.length} chars", link.length < 1000)
    }

    @Test
    fun importReplacesSharedDevicesAndKeepsLocalOnes() {
        val localOnly = SavedDevice("shellyplugmg3-0a1b2c3d4e5f", "192.0.2.14", "PlugMG3", 3, 1)
            .withPrefs(0, OutputPrefs("Socket", "Kitchen", "socket"))
        val localLamp = lamp.withPrefs(0, OutputPrefs("Old name", "", ""))
        val local = Household(listOf(localLamp, localOnly), listOf(outputKey(localOnly.id, 0)), listOf("Kitchen"))
        val shared = Household(listOf(lamp, ceiling), listOf(outputKey(ceiling.id, 0)), listOf("aisle", "kitchen"))

        val merged = SetupShare.merge(local, shared)

        assertEquals(3, merged.devices.size)
        assertEquals("Lamp", merged.devices.first { it.id == lamp.id }.nameOf(0))
        assertEquals("Socket", merged.devices.first { it.id == localOnly.id }.nameOf(0))
        assertEquals(listOf(outputKey(ceiling.id, 0), outputKey(localOnly.id, 0)), merged.outputOrder)
        // "Kitchen" is already in the shared order (other capitals), so it is not added twice.
        assertEquals(listOf("aisle", "kitchen"), merged.roomOrder)
    }

    /**
     * Tripwire for docs/SHARING.md: every stored setting must be classified exactly once as household (always shared),
     * preference (shared, taken over on request) or state (never shared). If this fails, you added a setting: decide
     * which it is, add it to the right set, and for household or preference also to Household/Preferences,
     * SetupShare, the doc and spec/fixtures/setup-share.json.
     */
    @Test
    fun everySettingIsClassifiedExactlyOnce() {
        val keys = SettingKeys::class.java.declaredFields
            .filter { Modifier.isStatic(it.modifiers) && it.type == String::class.java }
            .map { it.isAccessible = true; it.get(null) as String }
            .toSet()
        assertTrue("no keys found", keys.isNotEmpty())
        val sets = listOf(SettingKeys.HOUSEHOLD, SettingKeys.PREFERENCES, SettingKeys.STATE)
        val twice = keys.filter { k -> sets.count { k in it } > 1 }
        assertTrue("classified more than once: $twice", twice.isEmpty())
        val unclassified = keys - sets.flatten().toSet()
        assertTrue("not classified (see docs/SHARING.md): $unclassified", unclassified.isEmpty())
        // What SetupShare carries today; change together with Household/Preferences, SetupShare and docs/SHARING.md.
        assertEquals(setOf(SettingKeys.DEVICES, SettingKeys.OUTPUT_ORDER, SettingKeys.ROOM_ORDER), SettingKeys.HOUSEHOLD)
        assertEquals(setOf(SettingKeys.SWITCH_VIEW), SettingKeys.PREFERENCES)
    }
}
