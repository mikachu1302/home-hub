package io.github.mikachu1302.homehub.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import io.github.mikachu1302.homehub.core.AmpKeys
import io.github.mikachu1302.homehub.core.IrKey
import io.github.mikachu1302.homehub.core.TvIrKeys
import androidx.compose.foundation.layout.width

/**
 * Amp page: the keys the IR bridge defines (esphome/atom-lite-ir.yaml). IR is one-way, so the page shows no power,
 * volume or mute state (AGENTS.md); a failed send shows a message. Disabled until the bridge is set up.
 */
@Composable
fun AmpScreen(title: String, ready: Boolean, onPress: (IrKey, Boolean) -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = Dimens.screenPadding, vertical = Dimens.topPadding),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        if (!ready) Notice("Set up the IR bridge in Settings first.")
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.gap)) {
            AmpKey(AmpKeys.power, ready, onPress, Modifier.weight(1f))
            AmpKey(AmpKeys.mute, ready, onPress, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.gap)) {
            AmpKey(AmpKeys.inputPrevious, ready, onPress, Modifier.weight(1f), text = "‹ Input")
            AmpKey(AmpKeys.inputNext, ready, onPress, Modifier.weight(1f), text = "Input ›")
        }
        Column(
            Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.gap, Alignment.CenterVertically),
        ) {
            AmpKey(AmpKeys.volumeUp, ready, onPress, Modifier.size(160.dp, 120.dp), text = "+", repeats = true)
            Text("Volume", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AmpKey(AmpKeys.volumeDown, ready, onPress, Modifier.size(160.dp, 120.dp), text = "−", repeats = true)
        }
        Text(
            "Hold + or − to keep turning. Power, mute and input step on from wherever the amp is; it does not " +
                "report its state.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AmpKey(
    key: IrKey,
    enabled: Boolean,
    onPress: (IrKey, Boolean) -> Unit,
    modifier: Modifier,
    text: String = key.label,
    repeats: Boolean = false,
) {
    // A tap sends one step (onClick, which TalkBack also uses). A held [repeats] key sends one step at once and then
    // keeps sending; the click that ends the hold is then skipped.
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    var held by remember { mutableStateOf(false) }
    if (repeats) {
        LaunchedEffect(pressed) {
            if (!pressed) return@LaunchedEffect
            held = false
            delay(400)
            held = true
            onPress(key, false)
            while (true) {
                delay(200)
                onPress(key, true)
            }
        }
    }
    OutlinedButton(
        onClick = { if (held) held = false else onPress(key, false) },
        enabled = enabled,
        interactionSource = interaction,
        modifier = modifier.height(Dimens.bigKey),
    ) {
        Text(
            text,
            fontSize = if (text.length == 1) 32.sp else 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { contentDescription = key.label },
        )
    }
}

/**
 * TV over IR: power (a toggle, the TV reports nothing back over IR), mute, and the input menu with arrows, OK and
 * back. Volume stays with the network (tv-remote, later). Disabled until the bridge is set up.
 */
@Composable
fun TvIrScreen(title: String, ready: Boolean, onPress: (IrKey, Boolean) -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = Dimens.screenPadding, vertical = Dimens.topPadding),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        if (!ready) Notice("Set up the IR bridge in Settings first.")
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.gap)) {
            AmpKey(TvIrKeys.power, ready, onPress, Modifier.weight(1f))
            AmpKey(TvIrKeys.mute, ready, onPress, Modifier.weight(1f))
            AmpKey(TvIrKeys.source, ready, onPress, Modifier.weight(1f))
        }
        Column(
            Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.gap, Alignment.CenterVertically),
        ) {
            val key = Modifier.size(96.dp)
            AmpKey(TvIrKeys.up, ready, onPress, key, text = "▲")
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.gap)) {
                AmpKey(TvIrKeys.left, ready, onPress, key, text = "◀")
                AmpKey(TvIrKeys.ok, ready, onPress, key, text = "OK")
                AmpKey(TvIrKeys.right, ready, onPress, key, text = "▶")
            }
            AmpKey(TvIrKeys.down, ready, onPress, key, text = "▼")
            AmpKey(TvIrKeys.back, ready, onPress, Modifier.width(160.dp))
        }
        Text(
            "Source opens the TV's input menu; pick the input with the arrows and OK. The TV does not report its " +
                "state over IR.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** TV page, placeholder: the tv-remote screen moves in here later and gets the whole height. */
@Composable
fun TvScreen(title: String) {
    Box(Modifier.fillMaxSize().padding(Dimens.screenPadding), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Dimens.gap)) {
            Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(
                "The TV remote moves over from tv-remote later. It will use this whole screen.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun Notice(text: String) {
    Surface(
        shape = RoundedCornerShape(Dimens.corner),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text, fontSize = 14.sp, modifier = Modifier.padding(Dimens.rowPadding))
    }
}
