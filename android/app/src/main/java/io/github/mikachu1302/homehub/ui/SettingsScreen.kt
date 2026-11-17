package io.github.mikachu1302.homehub.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import io.github.mikachu1302.homehub.core.Appearance
import io.github.mikachu1302.homehub.core.UiStyle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import io.github.mikachu1302.homehub.core.Section
import io.github.mikachu1302.homehub.core.defaultTitle

/** The pages under Settings; null is the overview. */
private enum class SettingsPage(val title: String) { Devices("Devices"), Bridge("IR bridge"), Look("Look"), Share("Share") }

/**
 * Settings: an overview with one row per topic and a short summary; each row opens its own page (docs/DESIGN.md).
 * Back goes from a page to the overview, from the overview to the menu.
 */
@Composable
fun SettingsScreen(vm: HomeViewModel) {
    var page by rememberSaveable { mutableStateOf<SettingsPage?>(null) }
    BackHandler(enabled = page != null) { page = null }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(start = Dimens.screenPadding, end = Dimens.screenPadding, top = Dimens.topPadding, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Settings is not a full-height control page, so it gets a visible back key (the sections use the system
        // back only; docs/PLAN.md, "App layout").
        val current = page
        // tv-remote's settings header: the back key and a large title (no pane).
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            BackButton { if (current == null) vm.back() else page = null }
            Text(current?.title ?: "Settings", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(2.dp))
        when (current) {
            null -> Overview(vm) { page = it }
            SettingsPage.Devices -> DevicesPage(vm)
            SettingsPage.Bridge -> BridgePage(vm)
            SettingsPage.Look -> AppearanceSection(vm.appearance, vm::updateAppearance)
            SettingsPage.Share -> SharePage(vm)
        }
    }
}

@Composable
private fun Overview(vm: HomeViewModel, open: (SettingsPage) -> Unit) {
    val outputs = vm.devices.sumOf { it.channels }
    NavRow(
        "Devices",
        if (vm.devices.isEmpty()) "None yet" else "Shelly · ${vm.devices.size} devices, $outputs outputs",
    ) { open(SettingsPage.Devices) }
    NavRow(
        "IR bridge",
        when {
            vm.irBridge.isBlank() -> "Not set up"
            vm.bridgeCheck?.first == true -> "✓ Works"
            vm.bridgeCheck?.first == false -> "✗ Check failed"
            else -> "Set up"
        },
    ) { open(SettingsPage.Bridge) }
    NavRow("Look", "${vm.appearance.style.label} · ${Palettes.byId(vm.appearance.palette).name}") { open(SettingsPage.Look) }
    NavRow("Share", "With another phone") { open(SettingsPage.Share) }
}

@Composable
private fun DevicesPage(vm: HomeViewModel) {
    val colors = MaterialTheme.colorScheme
    TButton(
        onClick = { vm.discover() },
        enabled = !vm.discovering,
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) { Text(if (vm.discovering) "Searching…" else "Find devices") }
    InfoText("At home, on the Wi-Fi. Give devices fixed addresses in the router.")
    if (vm.devices.isNotEmpty()) {
        Section("Shelly · ${vm.devices.size} devices, ${vm.devices.sumOf { it.channels }} outputs")
        SectionCard {
            vm.devices.forEachIndexed { i, d ->
                if (i > 0) HorizontalDivider()
                Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("${d.app} · ${d.channels} output${if (d.channels > 1) "s" else ""}", fontWeight = FontWeight.Medium)
                    Text(
                        d.address + " · " + (0 until d.channels).joinToString(" · ") { d.nameOf(it) },
                        fontSize = 12.sp,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun BridgePage(vm: HomeViewModel) {
    var editingBridge by remember { mutableStateOf(false) }
    SectionCard {
        Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (vm.irBridge.isBlank()) {
                Text("Not set up", fontWeight = FontWeight.Medium)
                InfoText("Sends the IR codes for amplifier, TV and HDMI switch.")
            } else {
                Text(vm.irBridge, fontWeight = FontWeight.Medium)
                InfoText(vm.bridgeLogin?.let { "Login: ${it.user}" } ?: "No login")
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.gap)) {
        TOutlinedButton(onClick = { editingBridge = true }, modifier = Modifier.weight(1f).height(52.dp)) {
            Text(if (vm.irBridge.isBlank()) "Set up" else "Change")
        }
        if (vm.irBridge.isNotBlank()) {
            TOutlinedButton(
                onClick = vm::checkBridge,
                enabled = !vm.checkingBridge,
                modifier = Modifier.weight(1f).height(52.dp),
            ) { Text(if (vm.checkingBridge) "Checking…" else "Check") }
        }
    }
    if (!editingBridge) vm.bridgeCheck?.let { CheckResult(it) }
    if (editingBridge) {
        BridgeDialog(
            address = vm.irBridge,
            user = vm.bridgeLogin?.user ?: "",
            password = vm.bridgeLogin?.password ?: "",
            checking = vm.checkingBridge,
            result = vm.bridgeCheck,
            onCheck = vm::checkBridge,
            onSave = { a, u, p -> vm.saveBridge(a, u, p); editingBridge = false },
            onDismiss = { editingBridge = false },
        )
    }
}

@Composable
private fun SharePage(vm: HomeViewModel) {
    val context = LocalContext.current
    var sharing by remember { mutableStateOf(false) }
    TButton(
        onClick = { sharing = true },
        enabled = vm.devices.isNotEmpty() || vm.irBridge.isNotBlank(),
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) { Text("Show QR code") }
    TOutlinedButton(
        onClick = {
            val text = readClipboard(context)
            if (text.isNullOrBlank()) vm.message = "The clipboard is empty. Copy the setup link first."
            else vm.offerImport(text)
        },
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) { Text("Paste link") }
    if (sharing) ShareDialog(linkFor = vm::shareLink, hasPasswords = vm.bridgeLogin != null, onDismiss = { sharing = false })
}

/** A row that opens another page: bordered, with a summary and a chevron, so it reads as tappable (tv-remote). */
@Composable
private fun NavRow(title: String, subtitle: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = LocalTerm.current.controlShape
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .pane(shape)
            .semantics(mergeDescendants = true) { contentDescription = "$title, $subtitle"; role = Role.Button }
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontWeight = FontWeight.Medium, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, fontSize = 12.sp, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Glyph("right", colors.primary, Modifier.size(22.dp), strokeWidth = 2.6f)
    }
}

// ------------------------------------------------------------------ tv-remote's settings parts

/** A section headline: a divider, then the title in the "alive" colour. */
@Composable
private fun Section(title: String) {
    HorizontalDivider(Modifier.padding(top = 12.dp, bottom = 4.dp))
    Text(
        title,
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 4.dp).semantics { heading() },
    )
}

/** A bordered panel for a section's content. */
@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    val shape = LocalTerm.current.controlShape
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        content = content,
    )
}

@Composable
private fun InfoText(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

/** The way back from Settings: a large icon button (a text link is a small target). */
@Composable
private fun BackButton(onClick: () -> Unit) {
    val term = LocalTerm.current
    val shape = if (term.crt) term.controlShape else CircleShape
    Box(
        Modifier
            .size(52.dp)
            .clip(shape)
            .pane(shape)
            .semantics { contentDescription = "Back"; role = Role.Button }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Glyph("arrow-left", MaterialTheme.colorScheme.primary, Modifier.size(26.dp), strokeWidth = 2.6f)
    }
}

/** Style (two cards), scanlines for the CRT style, and the palettes: tv-remote's appearance section, without app icons. */
@Composable
private fun AppearanceSection(look: Appearance, onChange: ((Appearance) -> Appearance) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val term = LocalTerm.current

    Section("Style")
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        UiStyle.entries.forEach { style ->
            val selected = look.style == style
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(term.controlShape)
                    .border(if (selected) 2.dp else 1.dp, if (selected) colors.primary else colors.outline, term.controlShape)
                    .semantics { contentDescription = style.label + if (selected) ", selected" else ""; role = Role.RadioButton }
                    .clickable { onChange { it.copy(style = style) } },
            ) {
                Column(Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 30.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(style.label, fontWeight = FontWeight.Bold, color = if (selected) colors.primary else colors.onSurface)
                    Text(style.blurb, fontSize = 12.sp, color = colors.onSurfaceVariant)
                }
                if (selected) SelectedMark(Modifier.align(Alignment.TopEnd))
            }
        }
    }
    if (look.style == UiStyle.Crt) {
        SectionCard {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Scanlines", fontWeight = FontWeight.Medium)
                    InfoText("Like an old monitor.")
                }
                Switch(checked = look.scanlines, onCheckedChange = { on -> onChange { it.copy(scanlines = on) } })
            }
        }
    }

    Section("Colors")
    Palettes.all.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { palette ->
                PaletteChip(palette, palette.id == Palettes.byId(look.palette).id, Modifier.weight(1f).fillMaxHeight()) {
                    onChange { it.copy(palette = palette.id) }
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

/** A palette drawn in its own colours: name, four swatches, a check when selected. */
@Composable
private fun PaletteChip(palette: Palette, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = LocalTerm.current.controlShape
    Box(
        modifier
            .clip(shape)
            .background(palette.bg)
            .border(if (selected) 3.dp else 1.dp, if (selected) palette.primary else palette.border, shape)
            .semantics { contentDescription = palette.name + if (selected) ", selected" else ""; role = Role.RadioButton }
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 30.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(palette.name, color = palette.text, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            // Pride shows its six stripes, the others their four main colours.
            val dots = palette.stripes ?: listOf(palette.primary, palette.action, palette.soft, palette.text)
            Row(horizontalArrangement = Arrangement.spacedBy(if (dots.size > 4) 4.dp else 6.dp)) {
                dots.forEach { Box(Modifier.size(if (dots.size > 4) 16.dp else 18.dp).background(it, CircleShape)) }
            }
        }
        if (selected) SelectedMark(Modifier.align(Alignment.TopEnd), palette.primary)
    }
}

/** The check in a selected card's corner (drawn: the font's check mark looks like a root sign). */
@Composable
private fun SelectedMark(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Glyph("check", color, modifier.padding(8.dp).size(20.dp), strokeWidth = 2.8f)
}

/** Rename a menu section; "Default" goes back to the built-in title. */
@Composable
fun RenameDialog(current: String, default: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename") },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
        },
        confirmButton = { TTextButton(onClick = { onSave(name) }) { Text("Save") } },
        dismissButton = {
            Row {
                TTextButton(onClick = { onSave("") }) { Text("Default ($default)") }
                TTextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

/** Address and login of the IR bridge. The password is hidden; the login goes to the Keystore. */
@Composable
private fun BridgeDialog(
    address: String,
    user: String,
    password: String,
    checking: Boolean,
    result: Pair<Boolean, String>?,
    onCheck: (String, String, String) -> Unit,
    onSave: (String, String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var a by remember { mutableStateOf(address) }
    var u by remember { mutableStateOf(user) }
    var p by remember { mutableStateOf(password) }
    var showPassword by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("IR bridge") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = a,
                    onValueChange = { a = it },
                    label = { Text("Address") },
                    placeholder = { Text("192.168.x.x") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                OutlinedTextField(value = u, onValueChange = { u = it }, label = { Text("User") }, singleLine = true)
                OutlinedTextField(
                    value = p,
                    onValueChange = { p = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        TTextButton(onClick = { showPassword = !showPassword }) { Text(if (showPassword) "Hide" else "Show") }
                    },
                )
                TOutlinedButton(
                    onClick = { onCheck(a, u, p) },
                    enabled = !checking,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (checking) "Checking…" else "Check") }
                result?.let { CheckResult(it) }
                Text(
                    "User and password: web_username and web_password from its firmware.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TTextButton(onClick = { onSave(a, u, p) }) { Text("Save") } },
        dismissButton = { TTextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** A check result with a mark, so it does not rely on colour alone. */
@Composable
private fun CheckResult(result: Pair<Boolean, String>) {
    val (ok, text) = result
    Text(
        (if (ok) "✓ " else "✗ ") + text,
        fontSize = 13.sp,
        color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
    )
}
