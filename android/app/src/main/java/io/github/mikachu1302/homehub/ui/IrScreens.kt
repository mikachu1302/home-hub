package io.github.mikachu1302.homehub.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mikachu1302.homehub.core.AmpKeys
import io.github.mikachu1302.homehub.core.HdmiKeys
import io.github.mikachu1302.homehub.core.IrKey
import io.github.mikachu1302.homehub.core.TvIrKeys

/** Repeats of a held IR key: slower than tv-remote's 150 ms, the bridge needs about 0.1 to 0.3 s per key. */
const val IR_REPEAT_MS = 200L

/** Above this a press counts as slow (the bridge normally answers in about 0.1 to 0.3 s). */
private const val SLOW_MS = 1000L

/**
 * The IR pages, laid out like tv-remote's remote screen (docs/DESIGN.md): its header; the main control in the middle
 * ([pad], taking the space left, as tv-remote's pager); at the bottom tv-remote's row, the VOL rocker on the left and
 * the keys beside it ([cluster]), within thumb reach. Extra room below for the home-gesture area, as there.
 */
@Composable
private fun IrPage(
    title: String,
    ready: Boolean,
    status: IrStatus?,
    power: IrKey,
    volumeUp: IrKey,
    volumeDown: IrKey,
    onPress: (IrKey, Boolean) -> Unit,
    pad: @Composable () -> Unit,
    cluster: @Composable ColumnScope.() -> Unit,
    /** [pad] is the whole control group, centered on the page (the amp page); no bottom row. */
    grouped: Boolean = false,
) {
    val term = LocalTerm.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Fits without scrolling; only a very short window scrolls, with a fixed-size pad (tv-remote's rule).
        val tight = maxHeight < 600.dp
        Column(
            Modifier.fillMaxSize()
                .then(if (tight) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(start = 16.dp, end = 16.dp, top = Dimens.topPadding, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Power top right in the header, as tv-remote's PWR key.
            PageHeader(title, irStatusText(status, ready), irStatusColor(status)) {
                HeaderKey(power.label, "PWR", "power", enabled = ready, onPress = { onPress(power, false) })
            }
            if (!ready) Notice("Set up the IR bridge in Settings first.")
            val bottomRow = @Composable {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (term.crt) {
                        Rocker("VOL", volumeUp, volumeDown, ready, onPress, width = 72.dp, height = 184.dp)
                    } else {
                        Rocker("VOL", volumeUp, volumeDown, ready, onPress, width = 74.dp, height = 188.dp)
                    }
                    Column(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(if (term.crt) 6.dp else 8.dp),
                        content = cluster,
                    )
                }
            }
            if (grouped) {
                Box((if (tight) Modifier else Modifier.weight(1f)).fillMaxWidth(), contentAlignment = Alignment.Center) { pad() }
            } else {
                Box((if (tight) Modifier.height(280.dp) else Modifier.weight(1f)).fillMaxWidth(), contentAlignment = Alignment.Center) { pad() }
                bottomRow()
            }
        }
    }
}

/**
 * Amp page: Power in the header; VOL rocker with Mute below and the input rocker beside them, one centered group
 * (the user's call, 2026-11-17). IR is one-way, so no key shows a power, volume or mute state (AGENTS.md). Disabled until the
 * bridge is set up.
 */
@Composable
fun AmpScreen(title: String, ready: Boolean, status: IrStatus?, onPress: (IrKey, Boolean) -> Unit) {
    IrPage(
        title, ready, status, AmpKeys.power, AmpKeys.volumeUp, AmpKeys.volumeDown, onPress,
        grouped = true,
        pad = { AmpGroup(ready, onPress) },
        cluster = {},
    )
}

/**
 * The amp's controls as one centered group (the user's layout, 2026-11-17): on the left tv-remote's VOL rocker with
 * Mute below it, on the right the input as an upright rocker of the same total height (up = next, down = previous).
 */
@Composable
private fun AmpGroup(ready: Boolean, onPress: (IrKey, Boolean) -> Unit) {
    val term = LocalTerm.current
    val width = if (term.crt) 72.dp else 74.dp
    val rocker = if (term.crt) 184.dp else 188.dp
    val mute = if (term.crt) 56.dp else 60.dp
    val gap = 14.dp
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.Top) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(gap)) {
            Rocker("VOL", AmpKeys.volumeUp, AmpKeys.volumeDown, ready, onPress, width = width, height = rocker)
            if (term.crt) {
                IrKeyButton(AmpKeys.mute, "MUTE", "speaker-off", ready, onPress, Modifier.width(width).height(mute))
            } else {
                IrKeyButton(AmpKeys.mute, "MUTE", "speaker-off", ready, onPress, Modifier.size(mute), shape = CircleShape)
            }
        }
        InputRocker(AmpKeys.inputNext, AmpKeys.inputPrevious, ready, onPress, width, rocker + gap + mute)
    }
}

/** The amp's input as an upright rocker, drawn like the VOL rocker: › (next) on top, ‹ (previous) below. No repeat. */
@Composable
private fun InputRocker(next: IrKey, previous: IrKey, enabled: Boolean, onPress: (IrKey, Boolean) -> Unit, width: Dp, height: Dp) {
    val term = LocalTerm.current
    val p = term.palette
    val keyHeight = (height - 40.dp) / 2 // as the VOL rocker
    if (term.crt) {
        Column(Modifier.width(width).height(height), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
            RemoteButton(next.label, Modifier.fillMaxWidth().height(keyHeight), enabled = enabled, onPress = { onPress(next, false) }) {
                Triangle("up", it, Modifier.size(26.dp))
            }
            Text("INPUT", color = p.dim, fontSize = 14.sp, maxLines = 1)
            RemoteButton(previous.label, Modifier.fillMaxWidth().height(keyHeight), enabled = enabled, onPress = { onPress(previous, false) }) {
                Triangle("down", it, Modifier.size(26.dp))
            }
        }
    } else {
        Column(
            Modifier.width(width).height(height).pane(RoundedCornerShape(width / 2)).padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween,
        ) {
            RemoteButton(next.label, Modifier.fillMaxWidth().height(keyHeight - 8.dp), enabled = enabled, shape = RoundedCornerShape(width / 2), onPress = { onPress(next, false) }) {
                Glyph("up", it, Modifier.size(28.dp), 2.4f)
            }
            Text("Input", color = p.dim, fontSize = 11.sp, maxLines = 1)
            RemoteButton(previous.label, Modifier.fillMaxWidth().height(keyHeight - 8.dp), enabled = enabled, shape = RoundedCornerShape(width / 2), onPress = { onPress(previous, false) }) {
                Glyph("down", it, Modifier.size(28.dp), 2.4f)
            }
        }
    }
}

/**
 * TV over IR: Power in the header; tv-remote's dial in the middle (arrows and OK, for the input menu); at the bottom
 * the VOL rocker, Wake (Wake-on-LAN, from tv-remote: switches the TV on from deep standby), Mute and Back, and Source as the wide key where tv-remote has Play/Pause. Disabled until the bridge is set up.
 */
@Composable
fun TvIrScreen(title: String, ready: Boolean, status: IrStatus?, onPress: (IrKey, Boolean) -> Unit, onWake: () -> Unit) {
    val term = LocalTerm.current
    IrPage(
        title, ready, status, TvIrKeys.power, TvIrKeys.volumeUp, TvIrKeys.volumeDown, onPress,
        pad = {
            DPad(ready) { key, repeated ->
                onPress(
                    when (key) {
                        PadKey.Up -> TvIrKeys.up
                        PadKey.Down -> TvIrKeys.down
                        PadKey.Left -> TvIrKeys.left
                        PadKey.Right -> TvIrKeys.right
                        PadKey.Ok -> TvIrKeys.ok
                    },
                    repeated,
                )
            }
        },
        cluster = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                WakeKey(onWake)
                ClusterKey(TvIrKeys.mute, "MUTE", "speaker-off", ready, onPress)
                ClusterKey(TvIrKeys.back, "BACK", "back", ready, onPress)
            }
            IrKeyButton(
                TvIrKeys.source, "SOURCE", null, ready, onPress, Modifier.fillMaxWidth().height(56.dp),
                glassText = "Source", look = Look.Primary, shape = if (term.crt) null else RoundedCornerShape(28.dp), fontSize = 17,
            )
        },
    )
}

/**
 * Wake-on-LAN (tv-remote's "TV on"): not IR, a network packet to the TV's MAC address from the TV page's settings. Works
 * only from the TV's real standby, a few minutes after it was switched off; IR power works from either.
 */
@Composable
private fun RowScope.WakeKey(onWake: () -> Unit) {
    val term = LocalTerm.current
    val shape = if (term.crt) null else RoundedCornerShape(30.dp)
    RemoteButton("Wake the TV (Wake-on-LAN)", Modifier.weight(1f).height(60.dp), look = Look.Primary, shape = shape, onClick = onWake) {
        Text(if (term.crt) "WAKE" else "Wake", color = it, fontSize = if (term.crt) 18.sp else 16.sp, fontWeight = if (term.crt) FontWeight.Normal else FontWeight.SemiBold, maxLines = 1)
    }
}

/** A volume pair side by side (− VOL +) for custom-page blocks; both repeat while held. */
@Composable
fun VolumeRow(down: IrKey, up: IrKey, enabled: Boolean, onPress: (IrKey, Boolean) -> Unit, modifier: Modifier = Modifier) {
    val term = LocalTerm.current
    val p = term.palette
    @Composable
    fun Key(key: IrKey, text: String, m: Modifier) = RemoteButton(
        key.label, m.height(64.dp), enabled = enabled, repeat = true, repeatEveryMs = IR_REPEAT_MS, look = Look.Primary,
        shape = if (term.crt) null else CircleShape, onPress = { onPress(key, it) },
    ) { Text(text, color = it, fontSize = 30.sp, fontWeight = FontWeight.Bold, style = glowIf(term.crt && enabled, it)) }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Key(down, "−", Modifier.weight(1f))
        Text("VOL", color = p.dim, fontSize = 12.sp, maxLines = 1)
        Key(up, "+", Modifier.weight(1f))
    }
}

/** The HDMI switch (its own IR remote, docs/PROTOCOLS.md): five inputs and "next", as large keys. */
@Composable
fun HdmiScreen(title: String, ready: Boolean, status: IrStatus?, onPress: (IrKey, Boolean) -> Unit) {
    val term = LocalTerm.current
    val gap = if (term.crt) 8.dp else 12.dp
    Column(
        Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = Dimens.topPadding, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PageHeader(title, irStatusText(status, ready), irStatusColor(status))
        if (!ready) Notice("Set up the IR bridge in Settings first.")
        Column(Modifier.fillMaxWidth().heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(gap)) {
            val keys = HdmiKeys.inputs.mapIndexed { i, k -> Triple(k, "${i + 1}", Look.Normal) } +
                Triple(HdmiKeys.next, "NEXT", Look.Primary)
            keys.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { (key, text, look) ->
                        val input = HdmiKeys.inputs.indexOf(key)
                        IrKeyButton(
                            key, text, null, ready, onPress, Modifier.weight(1f).fillMaxHeight(),
                            glassText = if (text == "NEXT") "Next" else text, look = look, fontSize = if (text == "NEXT") 20 else 30,
                            tint = if (input >= 0) term.palette.stripes?.let { term.palette.accent(input) } else null,
                        )
                    }
                }
            }
        }
    }
}


// ------------------------------------------------------------------ parts

/** The last key's fate in one line: a mark and words, not colour alone. */
fun irStatusText(status: IrStatus?, ready: Boolean): String = when (status) {
    null -> if (ready) "Through the IR bridge" else "IR bridge not set up yet"
    is IrStatus.Sending -> "Sending ${status.key.label}…"
    is IrStatus.Sent ->
        "✓ ${status.key.label} sent · " + if (status.ms < SLOW_MS) "${status.ms} ms" else "slow (%.1f s)".format(status.ms / 1000.0)
    is IrStatus.Failed -> "✗ ${status.key.label} not sent: ${status.reason}"
}

@Composable
fun irStatusColor(status: IrStatus?): Color {
    val p = LocalTerm.current.palette
    return when (status) {
        is IrStatus.Sent -> if (status.ms < SLOW_MS) p.primary else p.warn
        is IrStatus.Failed -> p.warn
        else -> p.dim
    }
}

/** An IR key with a text label in the CRT style and, where there is one, an icon in the glass style. */
@Composable
fun IrKeyButton(
    key: IrKey,
    crtText: String,
    glyph: String?,
    enabled: Boolean,
    onPress: (IrKey, Boolean) -> Unit,
    modifier: Modifier,
    glassText: String = crtText,
    look: Look = Look.Normal,
    fontSize: Int = 16,
    shape: androidx.compose.ui.graphics.Shape? = null,
    /** Glass: icon and text side by side (wide keys) instead of the icon alone. */
    labelled: Boolean = false,
    tint: Color? = null,
) {
    val term = LocalTerm.current
    RemoteButton(key.label, modifier, enabled = enabled, look = look, tint = tint, shape = shape, onPress = { onPress(key, false) }) { color ->
        when {
            !term.crt && glyph != null && labelled -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Glyph(glyph, color, Modifier.size(24.dp))
                Text(glassText, color = color, fontSize = fontSize.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
            !term.crt && glyph != null -> Glyph(glyph, color, Modifier.size(26.dp))
            term.crt -> Text(crtText, color = color, fontSize = (fontSize + 2).sp, maxLines = 1)
            else -> Text(glassText, color = color, fontSize = fontSize.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

/** tv-remote's cluster key: a wide text key in the CRT style, a round icon key in the glass style. */
@Composable
fun RowScope.ClusterKey(key: IrKey, crtText: String, glyph: String?, enabled: Boolean, onPress: (IrKey, Boolean) -> Unit, glassText: String = crtText) {
    val term = LocalTerm.current
    if (term.crt || glyph == null) {
        IrKeyButton(
            key, crtText, glyph, enabled, onPress, Modifier.weight(1f).height(60.dp), glassText = glassText,
            shape = if (term.crt) null else RoundedCornerShape(30.dp),
        )
    } else {
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            IrKeyButton(key, crtText, glyph, enabled, onPress, Modifier.size(60.dp), shape = CircleShape)
        }
    }
}

/** tv-remote's rocker: + on top, − below, both repeat while held. */
@Composable
private fun Rocker(
    title: String,
    up: IrKey,
    down: IrKey,
    enabled: Boolean,
    onPress: (IrKey, Boolean) -> Unit,
    width: Dp,
    height: Dp,
) {
    val term = LocalTerm.current
    val p = term.palette
    val keyHeight = (height - 40.dp) / 2
    @Composable
    fun Key(key: IrKey, text: String, modifier: Modifier, shape: androidx.compose.ui.graphics.Shape?) = RemoteButton(
        key.label, modifier, enabled = enabled, repeat = true, repeatEveryMs = IR_REPEAT_MS, look = Look.Primary, shape = shape,
        onPress = { onPress(key, it) },
    ) {
        Text(text, color = it, fontSize = 40.sp, fontWeight = FontWeight.Bold, style = glowIf(term.crt && enabled, it))
    }
    if (term.crt) {
        Column(Modifier.width(width).height(height), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
            Key(up, "+", Modifier.fillMaxWidth().height(keyHeight), null)
            Text(title, color = p.dim, fontSize = 14.sp, maxLines = 1)
            Key(down, "−", Modifier.fillMaxWidth().height(keyHeight), null)
        }
    } else {
        Column(
            Modifier.width(width).height(height).pane(RoundedCornerShape(width / 2)).padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Key(up, "+", Modifier.fillMaxWidth().height(keyHeight - 8.dp), RoundedCornerShape(width / 2))
            Text(title, color = p.dim, fontSize = 12.sp, maxLines = 1)
            Key(down, "−", Modifier.fillMaxWidth().height(keyHeight - 8.dp), RoundedCornerShape(width / 2))
        }
    }
}

@Composable
fun Notice(text: String) {
    val p = LocalTerm.current.palette
    Box(Modifier.fillMaxWidth().pane(glowColor = p.warn)) {
        Text("⚠ $text", color = p.text, fontSize = 14.sp, modifier = Modifier.padding(Dimens.rowPadding))
    }
}

// ------------------------------------------------------------------ D-pad (tv-remote's CrtPad and GlassDial)

enum class PadKey(val label: String) { Up("Up"), Down("Down"), Left("Left"), Right("Right"), Ok("OK") }

/** The D-pad for the current style: a cross of square keys (CRT) or a round dial (glass). Arrows repeat while held. */
@Composable
fun DPad(enabled: Boolean, onKey: (PadKey, Boolean) -> Unit) {
    if (LocalTerm.current.crt) CrtPad(enabled, onKey) else GlassDial(enabled, onKey)
}

@Composable
private fun CrtPad(enabled: Boolean, onKey: (PadKey, Boolean) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val cell = minOf(92.dp, (maxHeight - 12.dp) / 3, (maxWidth - 12.dp) / 3)
        @Composable
        fun Arrow(key: PadKey, dir: String) = RemoteButton(
            key.label, Modifier.size(cell), enabled = enabled, repeat = true, repeatEveryMs = IR_REPEAT_MS,
            onPress = { onKey(key, it) },
        ) { Triangle(dir, it, Modifier.size(30.dp)) }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Arrow(PadKey.Up, "up")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Arrow(PadKey.Left, "left")
                RemoteButton("OK", Modifier.size(cell), enabled = enabled, look = Look.Action, strong = true, onPress = { onKey(PadKey.Ok, false) }) {
                    Text("OK", color = it, fontSize = 26.sp, fontWeight = FontWeight.Bold, style = glowStyle(it))
                }
                Arrow(PadKey.Right, "right")
            }
            Arrow(PadKey.Down, "down")
        }
    }
}

@Composable
private fun GlassDial(enabled: Boolean, onKey: (PadKey, Boolean) -> Unit) {
    val p = LocalTerm.current.palette
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // 272 dp when there is room (the glow around the ring needs a little air), smaller otherwise.
        val dial = minOf(272.dp, maxHeight - 16.dp, maxWidth - 16.dp)
        val s = dial / 272.dp
        val arrow = 64.dp * s
        val edge = 10.dp * s
        @Composable
        fun Arrow(key: PadKey, dir: String, modifier: Modifier) = RemoteButton(
            key.label, modifier.size(arrow), enabled = enabled, repeat = true, repeatEveryMs = IR_REPEAT_MS,
            ghost = true, shape = CircleShape, onPress = { onKey(key, it) },
        ) { Glyph(dir, it, Modifier.size(32.dp), 2.4f) }
        Box(Modifier.size(dial).dialRing(p), contentAlignment = Alignment.Center) {
            Arrow(PadKey.Up, "up", Modifier.align(Alignment.TopCenter).padding(top = edge))
            Arrow(PadKey.Down, "down", Modifier.align(Alignment.BottomCenter).padding(bottom = edge))
            Arrow(PadKey.Left, "left", Modifier.align(Alignment.CenterStart).padding(start = edge))
            Arrow(PadKey.Right, "right", Modifier.align(Alignment.CenterEnd).padding(end = edge))
            RemoteButton(
                "OK", Modifier.size(100.dp * s), enabled = enabled, look = Look.Action, strong = true, shape = CircleShape,
                onPress = { onKey(PadKey.Ok, false) },
            ) { Text("OK", color = it, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

/** The glass dial's face: soft glow, the ring fading between the two accents (or the Pride rainbow), a dark face. */
private fun Modifier.dialRing(p: Palette): Modifier = drawBehind {
    val r = size.minDimension / 2
    val center = Offset(r, r)
    drawCircle(p.action.copy(alpha = 0.10f), radius = r + 2.dp.toPx(), style = Stroke(16.dp.toPx()))
    drawCircle(p.primary.copy(alpha = 0.10f), radius = r - 2.dp.toPx(), style = Stroke(8.dp.toPx()))
    drawCircle(Brush.radialGradient(listOf(p.surface, p.bg), center = center, radius = r), radius = r - 2.dp.toPx())
    val ring = p.stripes?.let { it + it.first() } ?: listOf(p.primary, p.action, p.primary)
    drawCircle(Brush.sweepGradient(ring, center = center), radius = r - 2.dp.toPx(), style = Stroke(4.dp.toPx()))
}
