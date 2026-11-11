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
import io.github.mikachu1302.homehub.core.AmpKeys
import io.github.mikachu1302.homehub.core.IrKey

/**
 * Amp page: the keys the IR bridge defines (esphome/atom-lite-ir.yaml). IR is one-way, so the page shows no power,
 * volume or mute state (AGENTS.md); a failed send shows a message. Disabled until the bridge is set up.
 */
@Composable
fun AmpScreen(title: String, ready: Boolean, onPress: (IrKey) -> Unit) {
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
        Column(
            Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.gap, Alignment.CenterVertically),
        ) {
            AmpKey(AmpKeys.volumeUp, ready, onPress, Modifier.size(160.dp, 120.dp), text = "+")
            Text("Volume", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AmpKey(AmpKeys.volumeDown, ready, onPress, Modifier.size(160.dp, 120.dp), text = "−")
        }
        Text(
            "Power and mute switch back and forth; the amp does not report its state. Inputs follow once the " +
                "remote's codes are captured.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AmpKey(key: IrKey, enabled: Boolean, onPress: (IrKey) -> Unit, modifier: Modifier, text: String = key.label) {
    OutlinedButton(onClick = { onPress(key) }, enabled = enabled, modifier = modifier.height(Dimens.bigKey)) {
        Text(
            text,
            fontSize = if (text.length == 1) 32.sp else 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { contentDescription = key.label },
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
