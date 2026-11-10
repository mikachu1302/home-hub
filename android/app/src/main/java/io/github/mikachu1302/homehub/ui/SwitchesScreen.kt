@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package io.github.mikachu1302.homehub.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.heading
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
import io.github.mikachu1302.homehub.core.OutputIcon
import io.github.mikachu1302.homehub.core.SwitchConfig
import io.github.mikachu1302.homehub.core.SwitchView
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
    var arranging by remember { mutableStateOf(false) }
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
        if (outputs.isNotEmpty()) {
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                ) {
                    FilterChip(
                        selected = vm.switchView == SwitchView.ROOMS,
                        onClick = { vm.showView(SwitchView.ROOMS) },
                        label = { Text("By room") },
                    )
                    FilterChip(
                        selected = vm.switchView == SwitchView.ALL,
                        onClick = { vm.showView(SwitchView.ALL) },
                        label = { Text("All") },
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { arranging = !arranging }) { Text(if (arranging) "Done" else "Arrange") }
                }
            }
        }
        if (arranging && outputs.isNotEmpty()) {
            item(key = "arrange") { ArrangeContent(vm) }
        } else if (vm.switchView == SwitchView.ROOMS) {
            vm.roomGroups.forEach { g ->
                item(key = "room/${g.room}") {
                    Text(
                        g.room ?: "Other",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 2.dp).semantics { heading() },
                    )
                }
                items(g.items, key = { "${it.device.id}/${it.channel}" }) { o ->
                    OutputRow(o, showRoom = false, onToggle = { vm.toggle(o) }, onEdit = { editing = o.device.id to o.channel })
                }
            }
        } else {
            items(outputs, key = { "${it.device.id}/${it.channel}" }) { o ->
                OutputRow(o, showRoom = true, onToggle = { vm.toggle(o) }, onEdit = { editing = o.device.id to o.channel })
            }
        }
        if (outputs.isNotEmpty() && !arranging) {
            item {
                Text(
                    "Tap a name for name, room, icon and timers.",
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
            rooms = vm.rooms,
            onDismiss = { editing = null },
            onSave = { name, room, icon, config ->
                vm.save(current, name, room, icon, config)
                editing = null
            },
        )
    }
}

@Composable
private fun OutputRow(o: OutputUi, showRoom: Boolean, onToggle: () -> Unit, onEdit: () -> Unit) {
    // Unreachable: the last known state may be wrong, so it is not shown (show real state only).
    val on = o.reachable && o.status?.on == true
    val details = buildList {
        if (showRoom && o.room.isNotEmpty()) add(o.room)
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
            Box(
                Modifier.size(Dimens.iconBox).background(
                    if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(12.dp),
                ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    o.icon.vector(),
                    contentDescription = null, // the name says what it is; the button says on/off
                    tint = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(Modifier.width(Dimens.gap))
            Column(
                Modifier.weight(1f).clickable(onClickLabel = "Name, room, icon and timers", onClick = onEdit),
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
private fun TimerSheet(
    o: OutputUi,
    rooms: List<String>,
    onDismiss: () -> Unit,
    onSave: (String, String, OutputIcon, SwitchConfig?) -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf(o.name) }
    var room by remember { mutableStateOf(o.room) }
    var icon by remember { mutableStateOf(o.icon) }
    val start = o.config
    var autoOff by remember { mutableStateOf(start?.autoOff ?: false) }
    var offDelay by remember { mutableDoubleStateOf(start?.autoOffDelay ?: 420.0) }
    var autoOn by remember { mutableStateOf(start?.autoOn ?: false) }
    var onDelay by remember { mutableDoubleStateOf(start?.autoOnDelay ?: 60.0) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp),
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
            RoomPicker(room = room, rooms = rooms, onRoom = { room = it })
            IconPicker(icon = icon, onIcon = { icon = it })
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
                        onSave(name, room, icon, config)
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

/** Pick a room already used by another output, "No room", or type a new one. */
@Composable
private fun RoomPicker(room: String, rooms: List<String>, onRoom: (String) -> Unit) {
    val known = rooms.any { it.equals(room.trim(), ignoreCase = true) }
    var typing by remember { mutableStateOf(room.isNotBlank() && !known) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Room", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rooms.forEach { r ->
                FilterChip(
                    selected = !typing && r.equals(room.trim(), ignoreCase = true),
                    onClick = { typing = false; onRoom(r) },
                    label = { Text(r) },
                )
            }
            FilterChip(selected = !typing && room.isBlank(), onClick = { typing = false; onRoom("") }, label = { Text("No room") })
            FilterChip(selected = typing, onClick = { typing = true; if (known) onRoom("") }, label = { Text("New room…") })
        }
        if (typing) {
            OutlinedTextField(
                value = room,
                onValueChange = onRoom,
                label = { Text("New room") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun IconPicker(icon: OutputIcon, onIcon: (OutputIcon) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Icon", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutputIcon.entries.forEach { i ->
                FilterChip(
                    selected = i == icon,
                    onClick = { onIcon(i) },
                    leadingIcon = { Icon(i.vector(), contentDescription = null, modifier = Modifier.size(20.dp)) },
                    label = { Text(i.label) },
                )
            }
        }
    }
}

/** Arrange mode: drag handles for the rooms and for the outputs (within their room, or in the flat list). */
@Composable
private fun ArrangeContent(vm: HomeViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (vm.switchView == SwitchView.ROOMS) {
            val groups = vm.roomGroups
            val named = groups.filter { it.room != null }
            if (named.size > 1) {
                ArrangeHeading("Rooms")
                ReorderableList(
                    items = named,
                    keyOf = { "room/${it.room}" },
                    nameOf = { it.room!! },
                    onMove = vm::moveRoom,
                ) { g -> Text(g.room!!, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)) }
            }
            groups.forEach { g ->
                ArrangeHeading(g.room ?: "Other")
                ReorderableList(
                    items = g.items,
                    keyOf = { it.key },
                    nameOf = { it.name },
                    onMove = { from, delta -> vm.moveInRoom(g.room, from, delta) },
                ) { o -> ArrangeRow(o) }
            }
        } else {
            ReorderableList(
                items = vm.outputs,
                keyOf = { it.key },
                nameOf = { it.name },
                onMove = vm::moveInAll,
            ) { o -> ArrangeRow(o) }
        }
        Text(
            "Drag the handle to move. Tap Done when finished.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun ArrangeHeading(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 12.dp).semantics { heading() },
    )
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.ArrangeRow(o: OutputUi) {
    Icon(o.icon.vector(), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
    Spacer(Modifier.width(Dimens.gap))
    Text(o.name, fontSize = 16.sp, modifier = Modifier.weight(1f))
}
