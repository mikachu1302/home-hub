package io.github.mikachu1302.homehub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** True for keys a swipe can start on: inside a page that swipes between sections (tv-remote's `LocalSwipeable`). */
val LocalSwipeable = staticCompositionLocalOf { false }

/** How long a touch in a swipeable area waits to see whether it becomes a swipe. */
private const val SWIPE_WINDOW_MS = 100L

private class Released(val up: PointerInputChange?)

/** Which colour a key speaks in: plain, "alive" (plus/minus, on) or the action colour (OK). */
enum class Look { Normal, Primary, Action }

/**
 * Text glow for the CRT look, on top of the theme's text style. (A bare `TextStyle` as a Text's `style` replaces the
 * theme's style and with it the font, so styles here always start from [LocalTextStyle].)
 */
@Composable
fun glowStyle(color: Color): TextStyle = LocalTextStyle.current.merge(TextStyle(shadow = Shadow(color.copy(alpha = 0.75f), Offset.Zero, 14f)))

/** [glowStyle] when [on], else the theme's plain text style. */
@Composable
fun glowIf(on: Boolean, color: Color): TextStyle = if (on) glowStyle(color) else LocalTextStyle.current

/**
 * One key, drawn for the current style: tv-remote's `RemoteButton`, without its TV connection. [onPress] fires on
 * touch down (not release) so it feels immediate; with [repeat], holding the key calls it again every
 * [repeatEveryMs] after 400 ms, with `true` for the repeats. [onClick] fires on release, for keys that open or
 * toggle something. [active] marks a state that is on, in [activeColor]; [strong] keeps the glowing border;
 * [ghost] draws no background or border (arrows inside the glass dial). [content] gets the colour to draw in.
 * TalkBack's double tap is one press.
 */
@Composable
fun RemoteButton(
    description: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    repeat: Boolean = false,
    repeatEveryMs: Long = 150,
    look: Look = Look.Normal,
    active: Boolean = false,
    activeColor: Color? = null,
    /** A decorative colour for the label (Pride's flag colours on the HDMI keys); state colours still win. */
    tint: Color? = null,
    strong: Boolean = false,
    ghost: Boolean = false,
    shape: Shape? = null,
    onPress: ((repeated: Boolean) -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable (Color) -> Unit,
) {
    val term = LocalTerm.current
    val p = term.palette
    val haptic = LocalHapticFeedback.current
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    val press by rememberUpdatedState(onPress)
    val click by rememberUpdatedState(onClick)
    var pressed by remember { mutableStateOf(false) }
    val swipeable = LocalSwipeable.current

    val keyShape = shape ?: term.controlShape
    val accent = if (look == Look.Action) p.action else p.primary
    val glowColor = when {
        !enabled -> null
        active -> activeColor ?: accent
        strong -> accent
        else -> null
    }
    val contentColor = when {
        !enabled -> p.dim.copy(alpha = 0.45f)
        active -> activeColor ?: accent
        tint != null -> tint
        look == Look.Primary -> p.primary
        look == Look.Action -> p.action
        term.crt -> p.text
        else -> p.dim
    }
    val fill = when {
        ghost -> Color.Transparent
        pressed -> accent.copy(alpha = 0.30f).compositeOver(p.surface)
        strong && !term.crt -> p.bg
        else -> p.surface
    }
    val borderColor = when {
        glowColor != null -> glowColor
        !enabled -> p.border.copy(alpha = 0.5f)
        term.crt -> p.border
        else -> p.primary.copy(alpha = 0.30f)
    }
    val base = if (glowColor != null && !ghost) modifier.glow(glowColor, keyShape) else modifier
    Box(
        base
            .background(fill, keyShape)
            .let { if (ghost) it else it.border(if (glowColor != null || term.crt) 2.dp else 1.dp, borderColor, keyShape) }
            .semantics {
                contentDescription = description
                role = Role.Button
                if (!enabled) disabled()
                onClick {
                    if (!enabled) return@onClick false
                    press?.invoke(false)
                    click?.invoke()
                    true
                }
            }
            .pointerInput(enabled, repeat, repeatEveryMs, swipeable, haptics) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown()
                    pressed = true
                    // In a swipeable area the touch may be a swipe: wait briefly. If the pager takes the touch over,
                    // nothing is sent; a quick tap is sent as one press (tv-remote's rule).
                    var released: PointerInputChange? = null
                    if (swipeable) {
                        val early = withTimeoutOrNull(SWIPE_WINDOW_MS) { Released(waitForUpOrCancellation()) }
                        if (early != null && early.up == null) {
                            pressed = false
                            return@awaitEachGesture
                        }
                        released = early?.up
                    }
                    // A key that acts on release (in a scrolling list) gives its feedback on release, so a scroll
                    // that starts on it does not buzz.
                    if (press != null && haptics) haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    press?.invoke(false)
                    val ticker = if (repeat && press != null && released == null) scope.launch {
                        delay(400)
                        while (true) {
                            press?.invoke(true)
                            delay(repeatEveryMs)
                        }
                    } else null
                    try {
                        if ((released ?: waitForUpOrCancellation()) != null && click != null) {
                            if (press == null && haptics) haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                            click?.invoke()
                        }
                    } finally {
                        // Also runs when the key leaves the screen mid-press: never keep repeating.
                        ticker?.cancel()
                        pressed = false
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) { content(contentColor) }
}
