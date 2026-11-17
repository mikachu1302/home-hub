package io.github.mikachu1302.homehub.ui

import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.delay

/** "Haptic feedback" (Settings > Remote): a light vibration when a key is pressed, on every page. */
val LocalHaptics = staticCompositionLocalOf { true }

/** Idle time before theater mode dims the screen (tv-remote's value). */
private const val THEATER_IDLE_MS = 10_000L

/**
 * A control page (Amplifier, TV, Android TV, HDMI switch, the user's own pages) with tv-remote's two screen settings,
 * now for all of them (Settings > Remote): [keepAwake] keeps the screen on; [theater] dims it when nothing was touched
 * for a while, and any touch brings it back. Touches are only watched, never consumed.
 */
@Composable
fun ControlPage(keepAwake: Boolean, theater: Boolean, content: @Composable () -> Unit) {
    val view = LocalView.current
    DisposableEffect(keepAwake) {
        view.keepScreenOn = keepAwake
        onDispose { view.keepScreenOn = false }
    }
    // The last touch, kept outside Compose state: touches must not redraw the page.
    val lastTouch = remember { longArrayOf(SystemClock.uptimeMillis()) }
    Theater(theater, lastTouch)
    Box(
        Modifier.fillMaxSize().pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(PointerEventPass.Initial)
                    lastTouch[0] = SystemClock.uptimeMillis()
                }
            }
        },
    ) { content() }
}

/** Dims the screen when [lastTouch] is long ago; checks once a second (every 200 ms while dimmed), as tv-remote did. */
@Composable
private fun Theater(enabled: Boolean, lastTouch: LongArray) {
    val activity = LocalActivity.current ?: return
    val dimmed = remember { booleanArrayOf(false) }
    fun dim(on: Boolean) {
        if (on == dimmed[0]) return
        dimmed[0] = on
        activity.window.attributes = activity.window.attributes.also {
            it.screenBrightness = if (on) 0.01f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
    }
    LaunchedEffect(enabled) {
        while (true) {
            dim(enabled && SystemClock.uptimeMillis() - lastTouch[0] > THEATER_IDLE_MS)
            delay(if (dimmed[0]) 200 else 1_000)
        }
    }
    DisposableEffect(Unit) { onDispose { dim(false) } }
}
