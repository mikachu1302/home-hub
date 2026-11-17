package io.github.mikachu1302.homehub.ui

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import io.github.mikachu1302.homehub.core.Appearance

/**
 * Android cannot recolour a launcher icon, so there is one icon per palette, each behind its own manifest alias
 * (`tools/gen_launcher_icons.py` makes them; the same approach as tv-remote). Exactly one alias is enabled at a time;
 * switching it makes the launcher show the matching icon.
 */
object LauncherIcon {
    private const val NAMESPACE = "io.github.mikachu1302.homehub"
    private const val DEFAULT_ALIAS = "LauncherNeon"

    /** "neon" -> "LauncherNeon" (the alias names in the manifest). */
    fun aliasName(id: String) = "Launcher" + id.split('_').joinToString("") { it.replaceFirstChar { c -> c.uppercase() } }

    /** The alias for [look]: the icon of its palette (an unknown palette shows the default, as the app does). */
    fun aliasFor(look: Appearance): String = aliasName(Palettes.byId(look.palette).id)

    private val all = Palettes.all.map { aliasName(it.id) }

    /** Makes the alias for [look] the only enabled one. Does nothing when it already is. */
    fun apply(context: Context, look: Appearance) {
        val pm = context.packageManager
        val target = aliasFor(look)

        fun component(name: String) = ComponentName(context.packageName, "$NAMESPACE.$name")
        fun enabled(name: String): Boolean = when (pm.getComponentEnabledSetting(component(name))) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> false
            else -> name == DEFAULT_ALIAS // not set: the manifest's default
        }

        // Enable the new one first, so there is never a moment without a launcher entry.
        if (!enabled(target)) {
            pm.setComponentEnabledSetting(component(target), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        }
        all.filter { it != target && enabled(it) }.forEach {
            pm.setComponentEnabledSetting(component(it), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
    }
}
