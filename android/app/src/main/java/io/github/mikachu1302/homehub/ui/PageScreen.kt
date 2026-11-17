package io.github.mikachu1302.homehub.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import io.github.mikachu1302.homehub.core.PageSection
import io.github.mikachu1302.homehub.core.Section
import io.github.mikachu1302.homehub.tv.AndroidTvBlock
import io.github.mikachu1302.homehub.tv.ConnectWhileVisible
import io.github.mikachu1302.homehub.tv.Link
import io.github.mikachu1302.homehub.tv.RemoteViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
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
 * A page the user built from blocks (docs/DESIGN.md, "Custom pages"). With more than one section, everything under
 * the header swipes sideways between them, with tv-remote's dots below. "Edit" holds the page's name, the sections
 * and their blocks, and "Delete this page". A new, empty page opens in edit mode.
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
    val hasIr = page.blocks.any { it.kind != BlockKind.SWITCH && !it.kind.androidTv }
    val hasAtv = page.blocks.any { it.kind.androidTv }
    val ready = vm.irBridge.isNotBlank()
    // The Android TV page's view model (tv-remote's): connected while this page shows Android TV blocks.
    val tvVm = viewModel<RemoteViewModel>()
    if (hasAtv && !editing) ConnectWhileVisible(tvVm)
    val tvPaired = tvVm.paired.collectAsStateWithLifecycle().value
    val link = tvVm.link.collectAsStateWithLifecycle().value
    val p = LocalTerm.current.palette
    // IR blocks: the last key's fate; only Android TV blocks: the connection to the stick.
    val (status, statusColor) = when {
        editing -> null to null
        hasIr -> irStatusText(vm.irStatus, ready) to irStatusColor(vm.irStatus)
        hasAtv -> when (link) {
            Link.Connected -> "Connected to ${tvPaired?.name ?: "the stick"}" to p.primary
            Link.Connecting -> "Connecting…" to p.dim
            Link.Retrying -> "Can't reach the stick, retrying" to p.warn
            Link.PinChanged -> "Certificate changed, pair again on the Android TV page" to p.warn
            Link.Idle -> "Not connected" to p.dim
        }
        else -> null to null
    }
    val blocks = @Composable { list: List<Block>, swipeable: Boolean ->
        list.forEach { BlockView(it, vm, ready, tvVm, swipeable) }
    }
    val notices = @Composable {
        if (hasIr && !ready) Notice("Set up the IR bridge in Settings first.")
        if (hasAtv && tvPaired == null) Notice("Pair the Android TV stick on its page first.")
    }
    val header = @Composable {
        PageHeader(
            page.title,
            status,
            statusColor,
            trailing = { HeaderTextKey(if (editing) "Done editing" else "Edit this page", if (editing) "Done" else "Edit") { editing = !editing } },
        )
    }
    val padding = Modifier.padding(start = 16.dp, end = 16.dp, top = Dimens.topPadding)
    if (editing || page.sections.size <= 1) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).then(padding).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            header()
            if (editing) {
                EditPage(page, vm)
            } else {
                notices()
                blocks(page.blocks, false)
            }
        }
    } else {
        val pager = rememberPagerState { page.sections.size }
        Column(Modifier.fillMaxSize().then(padding).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            header()
            notices()
            HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.Top, pageSpacing = 16.dp) { i ->
                // A touch that starts a swipe must not press a key (tv-remote's rule).
                CompositionLocalProvider(LocalSwipeable provides true) {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 4.dp, bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        blocks(page.sections.getOrNull(i)?.blocks.orEmpty(), true)
                    }
                }
            }
            PagerDots(pager, Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

/** tv-remote's pager dots: one per section, the current one a longer bar. Tapping a dot goes to that section. */
@Composable
private fun PagerDots(state: PagerState, modifier: Modifier = Modifier) {
    val term = LocalTerm.current
    val p = term.palette
    val scope = rememberCoroutineScope()
    Row(modifier.height(24.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(state.pageCount) { index ->
            val selected = state.currentPage == index
            val width by animateDpAsState(if (selected) 22.dp else 8.dp, label = "dot")
            Box(
                Modifier
                    .size(width = 36.dp, height = 24.dp)
                    .semantics {
                        role = Role.Tab
                        this.selected = selected
                        contentDescription = "Section ${index + 1} of ${state.pageCount}"
                    }
                    .clickable { scope.launch { state.animateScrollToPage(index) } },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(width = width, height = 8.dp)
                        .background(if (selected) p.primary else if (p.stripes != null) p.accent(index) else p.border, if (term.crt) RoundedCornerShape(0.dp) else CircleShape),
                )
            }
        }
    }
}

// ------------------------------------------------------------------ blocks

@Composable
private fun BlockView(block: Block, vm: HomeViewModel, ready: Boolean, tvVm: RemoteViewModel, swipeable: Boolean) {
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
        if (block.kind.androidTv) {
            AndroidTvBlock(block.kind, tvVm, swipeable, onOpenTvPage = { vm.open(Section.TV) })
            return@Column
        }
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
            BlockKind.TV_IR_VOLUME -> VolumeRow(TvIrKeys.volumeDown, TvIrKeys.volumeUp, ready, press, Modifier.fillMaxWidth())
            BlockKind.HDMI -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                HdmiKeys.inputs.forEachIndexed { i, key ->
                    IrKeyButton(
                        key, "${i + 1}", null, ready, press, Modifier.weight(1f).height(60.dp), fontSize = 22,
                        tint = term.palette.stripes?.let { term.palette.accent(i) },
                    )
                }
                IrKeyButton(HdmiKeys.next, "NEXT", null, ready, press, Modifier.weight(1.5f).height(60.dp), glassText = "Next", look = Look.Primary)
            }
            else -> Unit
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
private fun EditPage(page: CustomPage, vm: HomeViewModel) {
    val p = LocalTerm.current.palette
    var title by rememberSaveable(page.id) { mutableStateOf(page.title) }
    var current by rememberSaveable(page.id) { mutableIntStateOf(0) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var removingSection by remember { mutableStateOf(false) }
    val index = current.coerceIn(0, page.sections.lastIndex)
    val section = page.sections[index]
    fun setSection(s: PageSection) = vm.updatePage(page.copy(sections = page.sections.mapIndexed { i, old -> if (i == index) s else old }))

    OutlinedTextField(
        value = title,
        onValueChange = {
            title = it
            if (it.isNotBlank()) vm.updatePage(page.copy(title = it.trim()))
        },
        label = { Text("Name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    // Sections: one tab each; the page swipes between them.
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        page.sections.forEachIndexed { i, _ ->
            FilterChip(selected = i == index, onClick = { current = i }, label = { Text(if (page.sections.size == 1) "Page" else "Section ${i + 1}") })
        }
        TTextButton(onClick = {
            vm.updatePage(page.copy(sections = page.sections + PageSection()))
            current = page.sections.size
        }) { Text("+ Section") }
    }
    if (page.sections.size > 1) Text("Swipe between sections on the page.", color = p.dim, fontSize = 12.sp)

    if (section.blocks.isEmpty()) {
        Text("No blocks yet.", color = p.dim, fontSize = 13.sp)
    } else {
        ReorderableList(
            items = section.blocks,
            keyOf = { it.kind.id + "/" + it.output },
            nameOf = { blockName(it, vm) },
            onMove = { from, delta -> setSection(section.copy(blocks = io.github.mikachu1302.homehub.core.moved(section.blocks, from, delta))) },
        ) { block ->
            Text(blockName(block, vm), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            TTextButton(onClick = { setSection(section.copy(blocks = section.blocks - block)) }) { Text("Remove") }
        }
    }
    TButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("+ Add block") }
    if (page.sections.size > 1) {
        TOutlinedButton(
            onClick = { if (section.blocks.isEmpty()) removeSection(page, index, vm) { current = it } else removingSection = true },
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { Text("Remove section ${index + 1}") }
    }
    TTextButton(onClick = { deleting = true }, modifier = Modifier.fillMaxWidth()) { Text("Delete this page", color = p.warn) }

    if (adding) {
        AddBlockDialog(
            section, vm,
            onAdd = { setSection(section.copy(blocks = section.blocks + it)); adding = false },
            onDismiss = { adding = false },
        )
    }
    if (removingSection) {
        AlertDialog(
            onDismissRequest = { removingSection = false },
            title = { Text("Remove section ${index + 1}?") },
            text = { Text("Its ${section.blocks.size} blocks go with it.") },
            confirmButton = { TTextButton(onClick = { removingSection = false; removeSection(page, index, vm) { current = it } }) { Text("Remove", color = p.warn) } },
            dismissButton = { TTextButton(onClick = { removingSection = false }) { Text("Cancel") } },
        )
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("Delete \"${page.title}\"?") },
            text = { Text("Devices are not affected.") },
            confirmButton = { TTextButton(onClick = { deleting = false; vm.deletePage(page.id) }) { Text("Delete", color = p.warn) } },
            dismissButton = { TTextButton(onClick = { deleting = false }) { Text("Cancel") } },
        )
    }
}

private fun removeSection(page: CustomPage, index: Int, vm: HomeViewModel, select: (Int) -> Unit) {
    vm.updatePage(page.copy(sections = page.sections.filterIndexed { i, _ -> i != index }))
    select((index - 1).coerceAtLeast(0))
}

private fun blockName(block: Block, vm: HomeViewModel): String =
    if (block.kind == BlockKind.SWITCH) "Switch · " + (vm.outputs.firstOrNull { it.key == block.output }?.name ?: "removed output")
    else block.kind.title

/** Blocks not in the section yet; "Switch" then asks which output. Each block appears once per section. */
@Composable
private fun AddBlockDialog(section: PageSection, vm: HomeViewModel, onAdd: (Block) -> Unit, onDismiss: () -> Unit) {
    val p = LocalTerm.current.palette
    var pickOutput by remember { mutableStateOf(false) }
    val used = section.blocks.toSet()
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
                if (rows.isEmpty()) Text(if (pickOutput) "Every switch is in this section already." else "Every block is in this section already.", color = p.dim)
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
