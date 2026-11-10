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
import io.github.mikachu1302.homehub.core.AmpKeys
import io.github.mikachu1302.homehub.core.IrKey

/**
 * Amp page, placeholder: the layout and keys exist, but they stay disabled until the IR bridge is set up
 * (docs/PLAN.md, phase 1). The keys are the ones esphome/atom-lite-ir.yaml already defines.
 */
@Composable
fun AmpScreen() {
    Column(
        Modifier.fillMaxSize().padding(horizontal = Dimens.screenPadding, vertical = Dimens.topPadding),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Amplifier", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Notice("The IR bridge is not set up yet. These keys start working once it is.")
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.gap)) {
            AmpKey(AmpKeys.power, Modifier.weight(1f))
            AmpKey(AmpKeys.mute, Modifier.weight(1f))
        }
        Column(
            Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.gap, Alignment.CenterVertically),
        ) {
            AmpKey(AmpKeys.volumeUp, Modifier.size(160.dp, 120.dp), text = "+")
            Text("Volume", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AmpKey(AmpKeys.volumeDown, Modifier.size(160.dp, 120.dp), text = "−")
        }
        Text("Inputs follow once the remote's codes are captured.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AmpKey(key: IrKey, modifier: Modifier, text: String = key.label) {
    OutlinedButton(onClick = {}, enabled = false, modifier = modifier.height(Dimens.bigKey)) {
        Text(text, fontSize = if (text.length == 1) 32.sp else 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** TV page, placeholder: the tv-remote screen moves in here later and gets the whole height. */
@Composable
fun TvScreen() {
    Box(Modifier.fillMaxSize().padding(Dimens.screenPadding), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Dimens.gap)) {
            Text("TV", fontSize = 24.sp, fontWeight = FontWeight.Bold)
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
