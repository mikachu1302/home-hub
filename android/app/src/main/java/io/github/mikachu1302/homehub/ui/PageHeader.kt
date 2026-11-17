package io.github.mikachu1302.homehub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Height of the header and of the keys in it. */
val HeaderKeySize = 52.dp

/**
 * The top row of every page, the same everywhere (docs/DESIGN.md): an optional key on the left (Settings: back), the
 * title with one status line under it, optional keys on the right. Not an app bar: it scrolls with the page.
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
    Row(
        Modifier.fillMaxWidth().heightIn(min = HeaderKeySize),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f)) {
            Text(
                if (term.crt) title.uppercase() else title,
                color = if (term.crt) p.primary else p.text,
                fontSize = if (term.crt) 24.sp else 22.sp,
                fontWeight = if (term.crt) FontWeight.Normal else FontWeight.Bold,
                style = glowIf(term.crt, p.primary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            if (status != null) {
                Text(
                    if (term.crt) status.uppercase() else status,
                    color = statusColor ?: p.dim,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp).semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
        trailing?.invoke()
    }
}

/** A square (CRT) or round (glass) icon key for the header: CRT shows [crtText], glass [glyph]. */
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

/** A text key for the header (for example "Arrange"). */
@Composable
fun HeaderTextKey(description: String, text: String, onClick: () -> Unit) {
    val term = LocalTerm.current
    RemoteButton(
        description, Modifier.height(HeaderKeySize).widthIn(min = HeaderKeySize),
        shape = if (term.crt) null else CircleShape, onClick = onClick,
    ) {
        Text(
            if (term.crt) text.uppercase() else text, color = it, fontSize = if (term.crt) 15.sp else 14.sp,
            fontWeight = if (term.crt) FontWeight.Normal else FontWeight.SemiBold, maxLines = 1,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}
