package io.github.mikachu1302.homehub.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mikachu1302.homehub.core.BlockKind
import io.github.mikachu1302.homehub.ui.Glyph
import io.github.mikachu1302.homehub.ui.LocalTerm
import io.github.mikachu1302.homehub.ui.glowStyle

/**
 * The Android TV blocks of custom pages (docs/DESIGN.md, "Custom pages"): pieces of tv-remote's remote screen with its
 * own keys, so they behave exactly as on the Android TV page (smooth press, haptics, the TV's own volume). The page
 * connects to the stick while it shows any of them ([ConnectWhileVisible]). [swipeable]: inside a page that swipes
 * between sections, so a swipe that starts on a key presses nothing (tv-remote's rule).
 */
@Composable
fun AndroidTvBlock(kind: BlockKind, vm: RemoteViewModel, swipeable: Boolean, onAddApp: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val term = LocalTerm.current
    CompositionLocalProvider(LocalSettings provides settings, LocalSwipeable provides swipeable) {
        when (kind) {
            BlockKind.ATV_APPS -> {
                val apps by vm.apps.collectAsStateWithLifecycle()
                val state by vm.tvState.collectAsStateWithLifecycle()
                // As on the Android TV page: only an app on a stick that is on counts as open.
                val current = state.appPackage?.takeIf { state.on != false }?.let { shortcutForPackage(it, state.appLabel) }
                AppRow(apps, current, vm, onAddApp = onAddApp)
            }
            BlockKind.ATV_PAD -> Box(Modifier.fillMaxWidth().height(250.dp), contentAlignment = Alignment.Center) {
                if (term.crt) CrtPad(vm) else GlassDial(vm)
            }
            BlockKind.ATV_KEYS -> Column(verticalArrangement = Arrangement.spacedBy(if (term.crt) 6.dp else 8.dp)) {
                val state by vm.tvState.collectAsStateWithLifecycle()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ClusterKey(
                        "PWR", "Power, " + when (state.on) { true -> "on"; false -> "off"; null -> "state unknown" },
                        "power", vm, Key.POWER, active = state.on == true,
                    )
                    ClusterKey("HOME", "Home", "home", vm, Key.HOME)
                    ClusterKey("BACK", "Back", "back", vm, Key.BACK)
                    ClusterKey("MENU", "Menu", "menu", vm, Key.MENU)
                }
                RemoteButton(
                    "Play or pause", Key.PLAY_PAUSE, vm, Modifier.fillMaxWidth().height(56.dp),
                    look = Look.Primary, shape = if (term.crt) null else RoundedCornerShape(28.dp),
                ) { color ->
                    if (term.crt) Text("PLAY / PAUSE", color = color, fontSize = 17.sp, style = glowStyle(color))
                    else Glyph("play", color, Modifier.size(26.dp))
                }
            }
            BlockKind.ATV_VOLUME -> VolumeBlock(vm)
            BlockKind.ATV_NUMBERS -> Box(Modifier.fillMaxWidth().height(260.dp)) { Keypad(vm) }
            BlockKind.ATV_MORE -> Box(Modifier.fillMaxWidth().height(320.dp)) { MoreKeysPage(vm) }
            else -> Unit
        }
    }
}

/** − VOL n + and mute in one row: tv-remote's volume keys (the TV's own volume over UPnP when it is set up). */
@Composable
private fun VolumeBlock(vm: RemoteViewModel) {
    val term = LocalTerm.current
    val p = term.palette
    val state by vm.tvState.collectAsStateWithLifecycle()
    val tvAudio by vm.audio.collectAsStateWithLifecycle()
    val volume = tvAudio?.let { Volume(it.level, 100, it.muted) } ?: state.volume
    val shape = if (term.crt) null else CircleShape
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        RemoteButton("Volume down", Key.VOLUME_DOWN, vm, Modifier.weight(1f).height(60.dp), repeat = true, look = Look.Primary, shape = shape) {
            Text("−", color = it, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            when {
                volume == null -> "VOL"
                volume.muted -> "MUTED"
                else -> "VOL ${volume.level}"
            },
            color = p.dim, fontSize = 12.sp, maxLines = 1, modifier = Modifier.width(64.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        RemoteButton("Volume up", Key.VOLUME_UP, vm, Modifier.weight(1f).height(60.dp), repeat = true, look = Look.Primary, shape = shape) {
            Text("+", color = it, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
        RemoteButton(
            "Mute, " + if (volume?.muted == true) "on" else "off", Key.VOLUME_MUTE, vm, Modifier.size(60.dp),
            active = volume?.muted == true, activeColor = p.action, shape = shape,
        ) { if (term.crt) Text("MUTE", color = it, fontSize = 13.sp) else Glyph(if (volume?.muted == true) "speaker-off" else "speaker", it, Modifier.size(24.dp)) }
    }
}
