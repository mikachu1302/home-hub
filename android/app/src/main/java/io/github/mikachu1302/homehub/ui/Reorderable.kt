package io.github.mikachu1302.homehub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

private val grip: ImageVector = ImageVector.Builder("grip", 24.dp, 24.dp, 24f, 24f)
    .addPath(addPathNodes("M5 8h14 M5 12h14 M5 16h14"), stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round)
    .build()

/**
 * A list with drag handles (same pattern as tv-remote's app shortcuts): hold the handle and drag past the
 * neighbours to move a row. Rows are keyed, so the dragged row keeps its gesture while the list reorders under it.
 * Screen readers get "Move up" / "Move down" on every row instead. [onMove] gets the index and the step.
 */
@Composable
fun <T> ReorderableList(
    items: List<T>,
    keyOf: (T) -> String,
    nameOf: (T) -> String,
    onMove: (from: Int, delta: Int) -> Unit,
    rowContent: @Composable RowScope.(T) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    val rowHeight = 60.dp
    val rowPx = with(LocalDensity.current) { rowHeight.toPx() }
    var dragging by remember { mutableStateOf<String?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    val lastIndex by rememberUpdatedState(items.lastIndex)
    val move by rememberUpdatedState(onMove)

    Column {
        items.forEachIndexed { index, item ->
            val k = keyOf(item)
            key(k) {
                val current by rememberUpdatedState(index)
                val isDragging = dragging == k
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(rowHeight)
                        .zIndex(if (isDragging) 1f else 0f)
                        .offset { IntOffset(0, if (isDragging) offset.roundToInt() else 0) }
                        .then(
                            if (isDragging) Modifier.background(colors.surfaceVariant, shape).border(1.dp, colors.primary, shape)
                            else Modifier,
                        )
                        .semantics {
                            customActions = listOf(
                                CustomAccessibilityAction("Move up") { move(current, -1); true },
                                CustomAccessibilityAction("Move down") { move(current, 1); true },
                            )
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .semantics { contentDescription = "Reorder ${nameOf(item)}" }
                            .pointerInput(k) {
                                detectDragGestures(
                                    onDragStart = {
                                        dragging = k
                                        offset = 0f
                                    },
                                    onDrag = { change, drag ->
                                        change.consume()
                                        offset += drag.y
                                        val target = (current + (offset / rowPx).roundToInt()).coerceIn(0, lastIndex)
                                        if (target != current) {
                                            move(current, target - current)
                                            offset -= (target - current) * rowPx
                                        }
                                    },
                                    onDragEnd = {
                                        dragging = null
                                        offset = 0f
                                    },
                                    onDragCancel = {
                                        dragging = null
                                        offset = 0f
                                    },
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) { Icon(grip, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(22.dp)) }
                    rowContent(item)
                }
            }
        }
    }
}
