package io.github.mikachu1302.homehub.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mikachu1302.homehub.core.Section
import io.github.mikachu1302.homehub.core.formatWatts

@Composable
fun MenuScreen(vm: HomeViewModel) {
    PollDevices(vm, withConfig = false)
    val outputs = vm.outputs
    val known = outputs.filter { it.reachable && it.status != null }
    val on = known.count { it.status?.on == true }
    val watts = known.sumOf { it.status?.watts ?: 0.0 }
    val switchesLine = when {
        outputs.isEmpty() -> "No devices yet"
        vm.allUnreachable -> "⚠ Can't reach the devices"
        known.isEmpty() -> "${outputs.size} outputs"
        else -> "$on of ${outputs.size} on · ${formatWatts(watts)}"
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.screenPadding, vertical = Dimens.topPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.gap),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Home Hub", fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { vm.open(Section.DEVICES) }) { Text("Devices") }
        }
        MenuEntry("Switches", switchesLine) { vm.open(Section.SWITCHES) }
        MenuEntry("Amplifier", "IR bridge not set up yet") { vm.open(Section.AMP) }
        MenuEntry("TV", "Coming later from tv-remote") { vm.open(Section.TV) }
    }
}

@Composable
private fun MenuEntry(title: String, line: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(Dimens.corner),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.menuEntryHeight),
    ) {
        Row(Modifier.padding(Dimens.rowPadding), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(line, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", fontSize = 28.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
