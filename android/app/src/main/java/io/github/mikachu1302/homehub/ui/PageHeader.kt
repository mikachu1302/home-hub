package io.github.mikachu1302.homehub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Size of the keys in the header: tv-remote's 52 dp squares (CRT) and 46 dp circles (glass). */
val HeaderKeySize: Dp
    @Composable get() = if (LocalTerm.current.crt) 52.dp else 46.dp

/**
 * The top of every page, drawn like tv-remote's header (docs/DESIGN.md): glass is one rounded pane with a round key on
 * either side, the title and a status chip under it; CRT is a plain row with square keys, the title as "> NAME" in the
 * alive colour and an upper-case status line. [status] in [statusColor] (the chip's dot and text); keys are optional.
 */
@Composable
fun PageHeader(
    title: String,
    status: String? = null,
    statusColor: Color? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val term = LocalTerm.current
    val p = term.palette
    val color = statusColor ?: p.dim
    val row = if (term.crt) {
        Modifier.fillMaxWidth().heightIn(min = HeaderKeySize)
    } else {
        Modifier.fillMaxWidth().pane(RoundedCornerShape(20.dp)).padding(12.dp)
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Row(row, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        leading?.invoke()
        Column(Modifier.weight(1f).padding(start = if (leading == null && !term.crt) 4.dp else 0.dp)) {
            if (term.crt) {
                Text(
                    "> " + title.uppercase(), color = p.primary, fontSize = 18.sp, style = glowStyle(p.primary),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() },
                )
                if (status != null) {
                    Text(
                        status.uppercase(), color = color, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            } else {
                Text(
                    title, color = p.text, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() },
                )
                if (status != null) {
                    StatusChip(status, color, Modifier.padding(top = 6.dp).semantics { liveRegion = LiveRegionMode.Polite })
                }
            }
        }
        trailing?.invoke()
    }
    // Pride and other flag palettes: a thin rainbow line under the header (Home Hub addition).
    FlagLine()
    }
}

/** tv-remote's status chip: a coloured dot and text in a faint pill of the same colour. */
@Composable
fun StatusChip(text: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier
            .background(color.copy(alpha = 0.12f), CircleShape)
            .border(1.dp, color.copy(alpha = 0.5f), CircleShape)
            .padding(horizontal = 9.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.padding(end = 5.dp).size(6.dp).background(color, CircleShape))
        Text(text, color = color, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A header key: tv-remote's square text key (CRT, [crtText]) or round icon key (glass, [glyph]). */
@Composable
fun HeaderKey(description: String, crtText: String, glyph: String, enabled: Boolean = true, onPress: (() -> Unit)? = null, onClick: (() -> Unit)? = null) {
    val term = LocalTerm.current
    RemoteButton(
        description, Modifier.size(HeaderKeySize), enabled = enabled, shape = if (term.crt) null else CircleShape,
        onPress = onPress?.let { f -> { _: Boolean -> f() } }, onClick = onClick,
    ) {
        if (term.crt) Text(crtText, color = it, fontSize = 13.sp, maxLines = 1) else Glyph(glyph, it, Modifier.size(22.dp))
    }
}

/** A text key for the header (for example "Edit"), as high as the other header keys. */
@Composable
fun HeaderTextKey(description: String, text: String, onClick: () -> Unit) {
    val term = LocalTerm.current
    RemoteButton(
        description, Modifier.height(HeaderKeySize).widthIn(min = HeaderKeySize),
        shape = if (term.crt) null else CircleShape, onClick = onClick,
    ) {
        Text(
            if (term.crt) text.uppercase() else text, color = it, fontSize = 13.sp,
            fontWeight = if (term.crt) FontWeight.Normal else FontWeight.SemiBold, maxLines = 1,
            modifier = Modifier.padding(horizontal = 14.dp),
        )
    }
}
