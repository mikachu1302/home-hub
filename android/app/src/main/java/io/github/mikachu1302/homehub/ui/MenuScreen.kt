package io.github.mikachu1302.homehub.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.mikachu1302.homehub.core.CustomPage
import io.github.mikachu1302.homehub.tv.RemoteViewModel
import io.github.mikachu1302.homehub.core.Section
import io.github.mikachu1302.homehub.core.defaultTitle
import io.github.mikachu1302.homehub.core.formatWatts
import io.github.mikachu1302.homehub.core.pageMenuId

/**
 * The start screen: one entry per built-in section and per custom page, in the user's order. "Edit" switches to a
 * list where entries are dragged into order and renamed, and new pages are added (docs/DESIGN.md).
 */
@Composable
fun MenuScreen(vm: HomeViewModel) {
    PollDevices(vm, withConfig = false)
    val p = LocalTerm.current.palette
    var editing by rememberSaveable { mutableStateOf(false) }
    // Back leaves edit mode first (registered after the app's own handler, so it wins).
    BackHandler(enabled = editing) { editing = false }
    var renaming by rememberSaveable { mutableStateOf<String?>(null) }
    val switches = switchesSummary(vm, p)
    // The paired stick (tv-remote's view model, shared with the Android TV page).
    val tvPaired = viewModel<RemoteViewModel>().paired.collectAsStateWithLifecycle().value?.name
    val bridge = (if (vm.irBridge.isBlank()) "IR Bridge not set up yet" else "Through the IR Bridge") to p.dim
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(start = Dimens.screenPadding, end = Dimens.screenPadding, top = Dimens.topPadding, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(Dimens.gap),
    ) {
        PageHeader(
            "Home Hub",
            trailing = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeaderTextKey(if (editing) "Done editing the menu" else "Edit the menu", if (editing) "Done" else "Edit") { editing = !editing }
                    HeaderKey("Settings", "SET", "gear", onClick = { vm.open(Section.SETTINGS) })
                }
            },
        )
        if (editing) {
            ReorderableList(items = vm.menu, keyOf = { it }, nameOf = vm::menuTitle, onMove = vm::moveMenu) { id ->
                Text(vm.menuTitle(id), fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                TTextButton(onClick = { renaming = id }) { Text("Rename") }
            }
            Text("Drag the handle to move. A name is also the page's title.", fontSize = 12.sp, color = p.dim)
            TOutlinedButton(
                onClick = {
                    val page = vm.newPage()
                    editing = false
                    vm.openPage(page.id)
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("+ New page") }
            Text("Build your own page from blocks: amplifier, TV, Android TV, HDMI Switch, switches.", fontSize = 12.sp, color = p.dim)
        } else {
            vm.menu.forEachIndexed { index, id ->
                val page = vm.pages.firstOrNull { pageMenuId(it) == id }
                if (page != null) {
                    MenuEntry(page.title, "pad", pageLine(page), p.dim, p.accent(index)) { vm.openPage(page.id) }
                } else {
                    val section = runCatching { Section.valueOf(id) }.getOrNull() ?: return@forEachIndexed
                    val (line, color) = when (section) {
                        Section.SWITCHES -> switches
                        Section.AMP, Section.TV_IR, Section.HDMI -> bridge
                        Section.TV -> (tvPaired?.let { "Paired with $it" } ?: "No stick paired yet") to p.dim
                        else -> "" to p.dim
                    }
                    MenuEntry(vm.title(section), glyphOf(section), line, color, p.accent(index)) { vm.open(section) }
                }
            }
        }
    }
    renaming?.let { id ->
        val page = vm.pages.firstOrNull { pageMenuId(it) == id }
        val section = runCatching { Section.valueOf(id) }.getOrNull()
        RenameDialog(
            current = vm.menuTitle(id),
            default = if (section != null) defaultTitle(section) else page?.title ?: "",
            onSave = { name ->
                if (page != null) {
                    if (name.isNotBlank()) vm.updatePage(page.copy(title = name.trim()))
                } else if (section != null) {
                    vm.renameSection(section, name)
                }
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }
}

/** A custom page's menu line: what it holds, by device. */
private fun pageLine(page: CustomPage): String =
    if (page.blocks.isEmpty()) "Empty · open it to add blocks"
    else page.blocks.map { it.kind.title.substringBefore(" · ") }.distinct().joinToString(" · ")

/** The Switches line: "alive" colour when something is on, the warning colour when nothing answers. */
fun switchesSummary(vm: HomeViewModel, p: Palette): Pair<String, Color> {
    val outputs = vm.outputs
    val known = outputs.filter { it.reachable && it.status != null }
    val on = known.count { it.status?.on == true }
    val watts = known.sumOf { it.status?.watts ?: 0.0 }
    return when {
        outputs.isEmpty() -> "No devices yet" to p.dim
        vm.allUnreachable -> "⚠ Can't reach the devices" to p.warn
        known.isEmpty() -> "${outputs.size} outputs" to p.dim
        else -> "$on of ${outputs.size} on · ${formatWatts(watts)}" to if (on > 0) p.primary else p.dim
    }
}

private fun glyphOf(section: Section) = when (section) {
    Section.SWITCHES -> "power"
    Section.AMP -> "speaker"
    Section.HDMI -> "input"
    else -> "tv"
}

/** One entry: a pane with the section's icon (glass), its title and status line, and a chevron. */
@Composable
private fun MenuEntry(title: String, glyph: String, line: String, lineColor: Color, iconColor: Color, onClick: () -> Unit) {
    val term = LocalTerm.current
    val p = term.palette
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.menuEntryHeight)
            .pane()
            .clip(term.controlShape)
            .semantics(mergeDescendants = true) { contentDescription = "$title, $line"; role = Role.Button }
            .clickable(onClick = onClick)
            .padding(horizontal = Dimens.rowPadding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (!term.crt) {
            // The icon in the entry's accent: the primary colour, or the next flag colour in a palette with a flag.
            Box(
                Modifier.size(Dimens.iconBox).background(p.surface, CircleShape)
                    .border(1.dp, if (p.stripes != null) iconColor.copy(alpha = 0.55f) else p.primary.copy(alpha = 0.30f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Glyph(glyph, iconColor, Modifier.size(22.dp))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                title, color = p.text, fontSize = if (term.crt) 22.sp else 19.sp,
                fontWeight = if (term.crt) FontWeight.Normal else FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (term.crt) line.uppercase() else line,
                color = lineColor,
                fontSize = 13.sp,
                style = glowIf(term.crt && lineColor == p.primary, lineColor),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Glyph("right", p.primary, Modifier.size(22.dp), strokeWidth = 2.6f)
    }
}
