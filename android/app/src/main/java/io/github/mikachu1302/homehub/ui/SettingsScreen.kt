package io.github.mikachu1302.homehub.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.mikachu1302.homehub.core.Section
import io.github.mikachu1302.homehub.core.defaultTitle

/** Settings: the devices, the IR bridge and sharing; later the preferences (theme …) too. */
@Composable
fun SettingsScreen(vm: HomeViewModel) {
    val context = LocalContext.current
    var sharing by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Section?>(null) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.screenPadding, vertical = Dimens.topPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.gap),
    ) {
        // Settings is not a full-height control page, so it gets a visible back button (the sections use the
        // system back only; docs/PLAN.md, "App layout").
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = vm::back, contentPadding = PaddingValues(start = 0.dp, end = 12.dp)) {
                Text("‹", fontSize = 28.sp, modifier = Modifier.semantics { contentDescription = "Back to the menu" })
            }
            Text("Settings", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            "Devices",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        Button(
            onClick = { vm.discover() },
            enabled = !vm.discovering,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text(if (vm.discovering) "Searching…" else "Find devices") }
        Text(
            "Run this at home on your Wi-Fi. Found devices then also work away from home through the VPN. " +
                "Give them fixed addresses in the router so they stay found.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
            "Shelly · ${vm.devices.size} devices, ${vm.devices.sumOf { it.channels }} outputs",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        vm.devices.forEach { d ->
            Surface(
                shape = RoundedCornerShape(Dimens.corner),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(Dimens.rowPadding), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("${d.app} · ${d.channels} output${if (d.channels > 1) "s" else ""}", fontWeight = FontWeight.SemiBold)
                    Text(
                        d.address + " · " + (0 until d.channels).joinToString(" · ") { d.nameOf(it) },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Text(
            "IR bridge",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        Notice("Not set up yet. It is added here once the ATOM Lite runs (esphome/README.md).")

        Heading("Menu")
        ReorderableList(
            items = vm.menu,
            keyOf = { it.name },
            nameOf = { vm.title(it) },
            onMove = vm::moveMenu,
        ) { section ->
            Text(vm.title(section), fontSize = 16.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { renaming = section }) { Text("Rename") }
        }
        Text(
            "Drag to reorder the menu. A name is also the page's title.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
            "Share with another phone",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.gap)) {
            Button(
                onClick = { sharing = true },
                enabled = vm.devices.isNotEmpty(),
                modifier = Modifier.weight(1f).height(52.dp),
            ) { Text("Show QR code") }
            OutlinedButton(
                onClick = {
                    val text = readClipboard(context)
                    if (text.isNullOrBlank()) vm.message = "The clipboard is empty. Copy the setup link first."
                    else vm.offerImport(text)
                },
                modifier = Modifier.weight(1f).height(52.dp),
            ) { Text("Paste link") }
        }
        Text(
            "Choose what to share: devices, names, rooms, icons and order, and your preferences. Scan the code with " +
                "the other phone's camera, or copy the link and send it.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    renaming?.let { section ->
        RenameDialog(
            current = vm.title(section),
            default = defaultTitle(section),
            onSave = { vm.renameSection(section, it); renaming = null },
            onDismiss = { renaming = null },
        )
    }
    if (sharing) ShareDialog(linkFor = vm::shareLink, onDismiss = { sharing = false })
}

@Composable
private fun Heading(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp).semantics { heading() },
    )
}

/** Rename a menu section; "Default" goes back to the built-in title. */
@Composable
private fun RenameDialog(current: String, default: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename") },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
        },
        confirmButton = { TextButton(onClick = { onSave(name) }) { Text("Save") } },
        dismissButton = {
            Row {
                TextButton(onClick = { onSave("") }) { Text("Default ($default)") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
