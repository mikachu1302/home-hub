package io.github.mikachu1302.homehub.ui

import androidx.compose.ui.graphics.Color
import io.github.mikachu1302.homehub.core.Appearance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.roundToInt

/** The launcher icons are generated from the palettes (`tools/gen_launcher_icons.py`): these fail when it was not re-run. */
class LauncherResourcesTest {
    private fun file(path: String): File =
        listOf("src/main/$path", "app/src/main/$path").map(::File).first { it.exists() }

    private fun hex(c: Color) = "#%02X%02X%02X".format((c.red * 255).roundToInt(), (c.green * 255).roundToInt(), (c.blue * 255).roundToInt())

    @Test
    fun everyPaletteHasAnIconInItsOwnColours() {
        val colors = file("res/values/launcher_colors.xml").readText()
        val manifest = file("AndroidManifest.xml").readText()
        Palettes.all.forEach { p ->
            val fg = file("res/drawable/ic_launcher_fg_${p.id}.xml").readText()
            // A palette with a flag draws the h in its flag colours, row by row; the others in the alive colour.
            val letter = p.stripes ?: listOf(p.primary)
            letter.forEach { assertTrue("${p.name}: letter colour ${hex(it)}", fg.contains(hex(it))) }
            assertTrue("${p.name}: cursor colour", fg.contains(hex(p.action)))
            assertTrue("${p.name}: background", colors.contains("ic_launcher_bg_${p.id}\">${hex(p.bg)}"))
            assertTrue("${p.name}: adaptive icon", file("res/mipmap-anydpi/ic_launcher_${p.id}.xml").exists())
            assertTrue("${p.name}: alias", manifest.contains(".${LauncherIcon.aliasName(p.id)}\""))
        }
    }

    @Test
    fun theLauncherEntryIsOnlyInTheAliases() {
        // A launcher filter on the activity itself would show a second icon that never changes colour.
        val manifest = file("AndroidManifest.xml").readText()
        val activity = manifest.substringAfter("<activity\n").substringBefore("</activity>")
        assertFalse(activity.contains("android.intent.category.LAUNCHER"))
        assertEquals(1, Regex("""android:enabled="true"""").findAll(manifest).count())
    }

    @Test
    fun iconsAreInsideTheLauncherSafeZone() {
        // Launchers show only the central ~66 of the 108 units; at 0.8 the farthest pixel corner is ~31 from the centre.
        val scale = Regex("""scaleX="([0-9.]+)"""")
        Palettes.all.forEach { p ->
            val s = scale.find(file("res/drawable/ic_launcher_fg_${p.id}.xml").readText())!!.groupValues[1].toDouble()
            assertTrue("${p.id}: scale $s is too large", s <= 0.8)
        }
    }

    @Test
    fun everyPaletteHasALaunchScreen() {
        assertEquals(Palettes.all.map { it.id }.toSet(), SPLASH_THEMES.keys)
        val styles = file("res/values/splash_themes.xml").readText()
        Palettes.all.forEach { assertTrue(it.id, styles.contains("@drawable/ic_launcher_fg_${it.id}<")) }
    }

    @Test
    fun aliasFollowsThePalette() {
        assertEquals("LauncherNeon", LauncherIcon.aliasFor(Appearance()))
        assertEquals("LauncherNord", LauncherIcon.aliasFor(Appearance(palette = "nord")))
        assertEquals("LauncherPride", LauncherIcon.aliasFor(Appearance(palette = "pride")))
        assertEquals("LauncherNeon", LauncherIcon.aliasFor(Appearance(palette = "unknown")))
    }
}
