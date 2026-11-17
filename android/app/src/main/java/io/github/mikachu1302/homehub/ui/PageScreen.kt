package io.github.mikachu1302.homehub.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mikachu1302.homehub.core.AmpKeys
import io.github.mikachu1302.homehub.core.Block
import io.github.mikachu1302.homehub.core.BlockKind
import io.github.mikachu1302.homehub.core.CustomPage
import io.github.mikachu1302.homehub.core.HdmiKeys
import io.github.mikachu1302.homehub.core.IrKey
import io.github.mikachu1302.homehub.core.TvIrKeys

/**
 * A page the user built from blocks (docs/DESIGN.md, "Custom pages"). "Edit" lists the blocks to drag, remove and
 * add, and holds the page's name and "Delete page". A new, empty page opens in edit mode.
 */
@Composable
fun CustomPageScreen(vm: HomeViewModel) {
    val page = vm.page
    if (page == null) {
        LaunchedEffect(Unit) { vm.back() }
        return
    }
    var editing by rememberSaveable(page.id) { mutableStateOf(page.blocks.isEmpty()) }
    // Back leaves edit mode first, then the page.
    BackHandler(enabled = editing) { editing = false }
    if (page.blocks.any { it.kind == BlockKind.SWITCH }) PollDevices(vm, withConfig = false)
    val hasIr = page.blocks.any { it.kind != BlockKind.SWITCH }
    val ready = vm.irBridge.isNotBlank()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = Dimens.topPadding, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PageHeader(
            page.title,
            if (hasIr && !editing) irStatusText(vm.irStatus, ready) else null,
            if (hasIr && !editing) irStatusColor(vm.irStatus) else null,
            trailing = { HeaderTextKey(if (editing) "Done editing" else "Edit this page", if (editing) "Done" else "Edit") { editing = !editing } },
        )
        if (editing) {
            EditBlocks(page, vm)
        } else {
            if (hasIr && !ready) Notice("Set up the IR bridge in Settings first.")
            page.blocks.forEach { BlockView(it, vm, ready) }
        }
    }
}

// ------------------------------------------------------------------ blocks

@Composable
private fun BlockView(block: Block, vm: HomeViewModel, ready: Boolean) {
    val term = LocalTerm.current
    val gap = if (term.crt) 6.dp else 8.dp
    val press: (IrKey, Boolean) -> Unit = vm::press
    if (block.kind == BlockKind.SWITCH) {
        val o = vm.outputs.firstOrNull { it.key == block.output }
        if (o == null) Notice("This switch is no longer in the device list.")
        else OutputRow(o, showRoom = true, onToggle = { vm.toggle(o) }, onEdit = null)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val label = block.kind.title.substringBefore(" · ")
        Text(if (term.crt) label.uppercase() else label, color = term.palette.dim, fontSize = 12.sp)
        when (block.kind) {
            BlockKind.AMP_VOLUME -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                RepeatKey(AmpKeys.volumeDown, "−", ready, press, Modifier.weight(1f))
                IrKeyButton(AmpKeys.mute, "MUTE", "speaker-off", ready, press, Modifier.weight(1f).height(64.dp))
                RepeatKey(AmpKeys.volumeUp, "+", ready, press, Modifier.weight(1f))
            }
            BlockKind.AMP_KEYS -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                IrKeyButton(AmpKeys.power, "PWR", "power", ready, press, Modifier.weight(1f).height(60.dp))
                IrKeyButton(AmpKeys.inputPrevious, "‹ INPUT", null, ready, press, Modifier.weight(1.3f).height(60.dp), glassText = "‹ Input")
                IrKeyButton(AmpKeys.inputNext, "INPUT ›", null, ready, press, Modifier.weight(1.3f).height(60.dp), glassText = "Input ›")
            }
            BlockKind.TV_IR_PAD -> {
                Box(Modifier.fillMaxWidth().height(250.dp), contentAlignment = Alignment.Center) {
                    DPad(ready) { key, repeated ->
                        press(
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
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    ClusterKey(TvIrKeys.back, "BACK", "back", ready, press)
                }
            }
            BlockKind.TV_IR_KEYS -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                ClusterKey(TvIrKeys.power, "PWR", "power", ready, press)
                ClusterKey(TvIrKeys.source, "SOURCE", null, ready, press, glassText = "Source")
                ClusterKey(TvIrKeys.mute, "MUTE", "speaker-off", ready, press)
            }
            BlockKind.HDMI -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                HdmiKeys.inputs.forEachIndexed { i, key ->
                    IrKeyButton(key, "${i + 1}", null, ready, press, Modifier.weight(1f).height(60.dp), fontSize = 22)
                }
                IrKeyButton(HdmiKeys.next, "NEXT", null, ready, press, Modifier.weight(1.5f).height(60.dp), glassText = "Next", look = Look.Primary)
            }
            BlockKind.SWITCH -> Unit
        }
    }
}

/** A volume key that repeats while held (tv-remote's rocker keys, side by side). */
@Composable
private fun RepeatKey(key: IrKey, text: String, enabled: Boolean, onPress: (IrKey, Boolean) -> Unit, modifier: Modifier) {
    RemoteButton(
        key.label, modifier.height(64.dp), enabled = enabled, repeat = true, repeatEveryMs = IR_REPEAT_MS, look = Look.Primary,
        shape = if (LocalTerm.current.crt) null else CircleShape, onPress = { onPress(key, it) },
    ) { Text(text, color = it, fontSize = 32.sp, fontWeight = FontWeight.Bold) }
}

// ------------------------------------------------------------------ editing

@Composable
private fun EditBlocks(page: CustomPage, vm: HomeViewModel) {
    val p = LocalTerm.current.palette
    var title by rememberSaveable(page.id) { mutableStateOf(page.title) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = title,
        onValueChange = {
            title = it
            if (it.isNotBlank()) vm.updatePage(page.copy(title = it.trim()))
        },
        label = { Text("Name (menu and title)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    if (page.blocks.isEmpty()) {
        Text("No blocks yet. Add the ones you need; they stack from top to bottom.", color = p.dim, fontSize = 13.sp)
    } else {
        ReorderableList(
            items = page.blocks,
            keyOf = { it.kind.id + "/" + it.output },
            nameOf = { blockName(it, vm) },
            onMove = { from, delta -> vm.updatePage(page.copy(blocks = io.github.mikachu1302.homehub.core.moved(page.blocks, from, delta))) },
        ) { block ->
            Text(blockName(block, vm), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            TTextButton(onClick = { vm.updatePage(page.copy(blocks = page.blocks - block)) }) { Text("Remove") }
        }
    }
    TButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("+ Add block") }
    TTextButton(onClick = { deleting = true }, modifier = Modifier.fillMaxWidth()) { Text("Delete this page", color = p.warn) }

    if (adding) {
        AddBlockDialog(
            page, vm,
            onAdd = { vm.updatePage(page.copy(blocks = page.blocks + it)); adding = false },
            onDismiss = { adding = false },
        )
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("Delete \"${page.title}\"?") },
            text = { Text("The page and its blocks are removed from this phone. Devices are not affected.") },
            confirmButton = { TTextButton(onClick = { deleting = false; vm.deletePage(page.id) }) { Text("Delete", color = p.warn) } },
            dismissButton = { TTextButton(onClick = { deleting = false }) { Text("Cancel") } },
        )
    }
}

private fun blockName(block: Block, vm: HomeViewModel): String =
    if (block.kind == BlockKind.SWITCH) "Switch · " + (vm.outputs.firstOrNull { it.key == block.output }?.name ?: "removed output")
    else block.kind.title

/** Blocks not on the page yet; "Switch" then asks which output. Each block appears once per page. */
@Composable
private fun AddBlockDialog(page: CustomPage, vm: HomeViewModel, onAdd: (Block) -> Unit, onDismiss: () -> Unit) {
    val p = LocalTerm.current.palette
    var pickOutput by remember { mutableStateOf(false) }
    val used = page.blocks.toSet()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (pickOutput) "Which switch?" else "Add a block") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                val rows: List<Pair<String, () -> Unit>> = if (pickOutput) {
                    vm.outputs.map { Block(BlockKind.SWITCH, it.key) to it }.filter { it.first !in used }
                        .map { (b, o) -> (o.name + if (o.room.isNotEmpty()) " · ${o.room}" else "") to { onAdd(b) } }
                } else {
                    BlockKind.entries.filter { it.needsOutput || Block(it) !in used }.map { kind ->
                        kind.title to { if (kind.needsOutput) pickOutput = true else onAdd(Block(kind)) }
                    }
                }
                if (rows.isEmpty()) Text(if (pickOutput) "Every switch is on this page already." else "Every block is on this page already.", color = p.dim)
                rows.forEachIndexed { i, (name, action) ->
                    if (i > 0) HorizontalDivider()
                    Text(
                        name,
                        fontSize = 16.sp,
                        modifier = Modifier.fillMaxWidth().semantics { role = Role.Button }.clickable(onClick = action)
                            .padding(vertical = 14.dp),
                    )
                }
            }
        },
        confirmButton = { TTextButton(onClick = { if (pickOutput) pickOutput = false else onDismiss() }) { Text(if (pickOutput) "Back" else "Close") } },
    )
}
