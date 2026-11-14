package io.github.mikachu1302.homehub.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Made-up devices and addresses throughout. */
class ModelTest {
    private val plug = SavedDevice("shellyplugmg3-0a1b2c3d4e5f", "192.0.2.14", "PlugMG3", 3, 1, mapOf(0 to OutputPrefs("Socket", "Kitchen")))
    private val twoPm = SavedDevice("shellyplus2pm-a0b1c2d3e4f5", "192.0.2.10", "Plus2PM", 2, 2)

    @Test
    fun storageRoundTrip() {
        val list = listOf(plug, twoPm.withPrefs(1, OutputPrefs("Lamp", "Living room", "lamp")))
        assertEquals(list, DeviceJson.decode(DeviceJson.encode(list)))
    }

    @Test
    fun brokenStorageIsEmpty() {
        assertEquals(emptyList<SavedDevice>(), DeviceJson.decode("not json"))
        assertEquals(emptyList<SavedDevice>(), DeviceJson.decode(null))
    }

    @Test
    fun mergeKeepsNamesAndTakesNewAddress() {
        val found = listOf(plug.copy(address = "192.0.2.99", outputs = emptyMap()), twoPm)
        val merged = mergeDevices(listOf(plug), found)
        assertEquals(2, merged.size)
        val p = merged.first { it.id == plug.id }
        assertEquals("192.0.2.99", p.address)
        assertEquals(OutputPrefs("Socket", "Kitchen"), p.prefsOf(0))
    }

    @Test
    fun defaultNames() {
        assertEquals("Socket", plug.nameOf(0))
        assertEquals("Plus2PM · 1", twoPm.nameOf(0))
        assertEquals("Plus2PM · 2", twoPm.nameOf(1))
        assertEquals("Plus2PM · 1", twoPm.withPrefs(0, OutputPrefs("  ")).nameOf(0))
    }

    @Test
    fun oldStorageWithOnlyNamesStillLoads() {
        val old = """[{"id":"shellyplus1pm-000000000001","address":"192.0.2.11","app":"Plus1PM","gen":2,"channels":1,"names":{"0":"Lamp"}}]"""
        assertEquals(OutputPrefs("Lamp", ""), DeviceJson.decode(old).single().prefsOf(0))
    }

    @Test
    fun roomsInUseAreUniqueIgnoringCase() {
        val devices = listOf(plug, twoPm.withPrefs(0, OutputPrefs("", "kitchen")).withPrefs(1, OutputPrefs("", "Aisle")))
        assertEquals(listOf("Aisle", "Kitchen"), roomsInUse(devices))
    }

    @Test
    fun unknownIconFallsBackToGeneric() {
        assertEquals(OutputIcon.LAMP, twoPm.withPrefs(0, OutputPrefs(icon = "lamp")).iconOf(0))
        assertEquals(OutputIcon.GENERIC, twoPm.withPrefs(0, OutputPrefs(icon = "spaceship")).iconOf(0))
        assertEquals(OutputIcon.GENERIC, twoPm.iconOf(1))
    }

    @Test
    fun savedOrderFirstNewOnesAfter() {
        val items = listOf("a", "b", "c", "d")
        assertEquals(listOf("c", "a", "b", "d"), sortByOrder(items, { it }, listOf("c", "a", "gone")))
    }

    @Test
    fun movedClampsAtTheEnds() {
        val l = listOf(1, 2, 3)
        assertEquals(listOf(2, 1, 3), moved(l, 0, 1))
        assertEquals(listOf(2, 3, 1), moved(l, 0, 5))
        assertEquals(l, moved(l, 0, -1))
        assertEquals(l, moved(l, 7, 1))
    }

    @Test
    fun menuFollowsOrderAndNames() {
        assertEquals(MENU_SECTIONS, menuSections(emptyList()))
        // Saved order first; unknown ids ignored; a section missing from the order (added later) comes last.
        assertEquals(listOf(Section.TV, Section.SWITCHES, Section.AMP, Section.TV_IR), menuSections(listOf("TV", "GONE", "SWITCHES")))
        assertEquals("Lights", sectionTitle(Section.SWITCHES, mapOf("SWITCHES" to " Lights ")))
        assertEquals("Amplifier", sectionTitle(Section.AMP, mapOf("AMP" to "  ")))
    }

    @Test
    fun setConfigOnlySendsTimers() {
        val p = ShellyJson.setConfigParams(SwitchConfig(1, true, 420.0, false, 60.0))
        assertEquals(1, p.getInt("id"))
        val c = p.getJSONObject("config")
        assertEquals(setOf("auto_off", "auto_off_delay", "auto_on", "auto_on_delay"), c.keys().asSequence().toSet())
        assertEquals(420.0, c.getDouble("auto_off_delay"), 0.0)
    }

    @Test
    fun requestBody() {
        val o = JSONObject(ShellyJson.request(3, "Switch.Set", ShellyJson.setParams(0, true)))
        assertEquals("Switch.Set", o.getString("method"))
        assertTrue(o.getJSONObject("params").getBoolean("on"))
    }

    @Test
    fun irPathEncodesSpaces() {
        assertEquals("/button/Amp%20Volume%20Up/press", irPressPath(AmpKeys.volumeUp))
    }

    @Test
    fun watts() {
        assertEquals("0 W", formatWatts(0.0))
        assertEquals("7.6 W", formatWatts(7.6))
    }
}
