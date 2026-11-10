@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package io.github.mikachu1302.homehub.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.mikachu1302.homehub.core.DELAY_PRESETS
import io.github.mikachu1302.homehub.core.Section
import io.github.mikachu1302.homehub.core.SwitchConfig
import io.github.mikachu1302.homehub.core.formatDelay
import io.github.mikachu1302.homehub.core.formatWatts
import kotlinx.coroutines.delay

/** Reads the devices while this screen is visible: timers once, the switch states every few seconds. */
@Composable
fun PollDevices(vm: HomeViewModel, withConfig: Boolean) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var first = true
            while (true) {
                // The loop must never die: an unexpected error only skips this round.
                try {
                    vm.refresh(withConfig && first)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
                first = false
                delay(5_000)
            }
        }
    }
}

@Composable
fun SwitchesScreen(vm: HomeViewModel) {
    PollDevices(vm, withConfig = true)
    var editing by remember { mutableStateOf<Pair<String, Int>?>(null) }
    val outputs = vm.outputs

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = Dimens.screenPadding, end = Dimens.screenPadding, top = Dimens.topPadding, bottom = 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Text("Switches", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp)) }
        if (vm.allUnreachable) {
            item { ConnectionProblem(onRetry = vm::retryNow) }
        }
        if (outputs.isEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.gap)) {
                    Text("No devices yet. Find them on the home Wi-Fi first.")
                    Button(onClick = { vm.open(Section.DEVICES) }) { Text("Find devices") }
                }
            }
        }
        items(outputs, key = { "${it.device.id}/${it.channel}" }) { o ->
            OutputRow(o, onToggle = { vm.toggle(o) }, onEdit = { editing = o.device.id to o.channel })
        }
        if (outputs.isNotEmpty()) {
            item {
                Text(
                    "Tap a name for timers and rename.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                )
            }
        }
    }

    val current = outputs.firstOrNull { (it.device.id to it.channel) == editing }
    if (current != null) {
        TimerSheet(
            o = current,
            onDismiss = { editing = null },
            onSave = { name, config ->
                vm.save(current, name, config)
                editing = null
            },
        )
    }
}

@Composable
private fun OutputRow(o: OutputUi, onToggle: () -> Unit, onEdit: () -> Unit) {
    // Unreachable: the last known state may be wrong, so it is not shown (show real state only).
    val on = o.reachable && o.status?.on == true
    val details = buildList {
        if (!o.reachable) add("No connection")
        else o.status?.watts?.let { add(formatWatts(it)) }
        o.config?.let { c ->
            if (c.autoOff) add("⏱ Auto-off ${formatDelay(c.autoOffDelay)}")
            if (c.autoOn) add("⏱ Auto-on ${formatDelay(c.autoOnDelay)}")
        }
    }.joinToString(" · ")
    Surface(
        shape = RoundedCornerShape(Dimens.corner),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(Modifier.fillMaxWidth().padding(Dimens.rowPadding), verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier.weight(1f).clickable(onClickLabel = "Timers and name", onClick = onEdit),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(o.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                if (details.isNotEmpty()) Text(details, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(Dimens.gap))
            val known = o.reachable && o.status != null
            val label = when {
                o.busy -> "…"
                !known -> "–"
                on -> "On"
                else -> "Off"
            }
            val description = "${o.name}: ${if (!known) "state unknown" else if (on) "on" else "off"}. Tap to switch."
            val modifier = Modifier.width(Dimens.toggleWidth).height(Dimens.buttonHeight).semantics { contentDescription = description }
            val enabled = !o.busy && o.reachable
            if (on) {
                Button(onClick = onToggle, enabled = enabled, modifier = modifier) { Text(label, fontWeight = FontWeight.Bold) }
            } else {
                OutlinedButton(onClick = onToggle, enabled = enabled, modifier = modifier) { Text(label, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun TimerSheet(o: OutputUi, onDismiss: () -> Unit, onSave: (String, SwitchConfig?) -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf(o.name) }
    val start = o.config
    var autoOff by remember { mutableStateOf(start?.autoOff ?: false) }
    var offDelay by remember { mutableDoubleStateOf(start?.autoOffDelay ?: 420.0) }
    var autoOn by remember { mutableStateOf(start?.autoOn ?: false) }
    var onDelay by remember { mutableDoubleStateOf(start?.autoOnDelay ?: 60.0) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "${o.device.app} · channel ${o.channel + 1} of ${o.device.channels}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider()
            if (start == null) {
                Text("Timers not loaded yet (device not reachable).", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                TimerBlock("Auto-off", "Turns off this long after it was switched on", autoOff, { autoOff = it }, offDelay) { offDelay = it }
                HorizontalDivider()
                TimerBlock("Auto-on", "Turns on this long after it was switched off", autoOn, { autoOn = it }, onDelay) { onDelay = it }
                Text(
                    "Saved on the device: works without this app and without internet.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.gap)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(52.dp)) { Text("Cancel") }
                Button(
                    onClick = {
                        val config = start?.copy(autoOff = autoOff, autoOffDelay = offDelay, autoOn = autoOn, autoOnDelay = onDelay)
                        onSave(name, config)
                    },
                    modifier = Modifier.weight(1f).height(52.dp),
                ) { Text(if (start == null) "Save name" else "Save") }
            }
        }
    }
}

@Composable
private fun TimerBlock(
    title: String,
    explanation: String,
    enabled: Boolean,
    onEnabled: (Boolean) -> Unit,
    delaySeconds: Double,
    onDelay: (Double) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(explanation, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = enabled, onCheckedChange = onEnabled, modifier = Modifier.semantics { contentDescription = title })
        }
        if (enabled) {
            val isPreset = DELAY_PRESETS.any { it == delaySeconds }
            var custom by remember { mutableStateOf(!isPreset) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DELAY_PRESETS.forEach { preset ->
                    FilterChip(
                        selected = !custom && preset == delaySeconds,
                        onClick = { custom = false; onDelay(preset) },
                        label = { Text(formatDelay(preset)) },
                    )
                }
                FilterChip(selected = custom, onClick = { custom = true }, label = { Text("Custom") })
            }
            if (custom) {
                var minutes by remember { mutableStateOf(formatMinutes(delaySeconds)) }
                OutlinedTextField(
                    value = minutes,
                    onValueChange = { text ->
                        minutes = text
                        text.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 && it <= 24 * 60 }?.let { onDelay(Math.round(it * 60).toDouble()) }
                    },
                    label = { Text("Minutes") },
                    supportingText = { Text("Now: ${formatDelay(delaySeconds)}") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun formatMinutes(seconds: Double): String {
    val m = seconds / 60
    return if (m == Math.floor(m)) m.toLong().toString() else "%.1f".format(java.util.Locale.ROOT, m)
}

@Composable
private fun ConnectionProblem(onRetry: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(Dimens.corner),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Dimens.rowPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("⚠ Can't reach the devices", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Check the Wi-Fi, or the VPN when you are away. The app keeps trying every few seconds.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onRetry) { Text("Try now") }
        }
    }
}
