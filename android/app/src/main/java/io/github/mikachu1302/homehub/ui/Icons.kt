package io.github.mikachu1302.homehub.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import io.github.mikachu1302.homehub.core.OutputIcon

/**
 * Output icons, drawn here from path data (24 x 24, stroke 2), so no icon library is needed and every phone shows
 * the same shapes (fonts lack many symbols). Drawn for this app. Tint comes from the `Icon` that shows them.
 */
private val PATHS: Map<OutputIcon, String> = mapOf(
    // A power symbol.
    OutputIcon.GENERIC to "M12 3v8 M7.1 6.3a7 7 0 1 0 9.8 0",
    // Pendant from the ceiling: cord, dome shade, bulb below.
    OutputIcon.CEILING to "M12 2v5 M5 14a7 7 0 0 1 14 0z M10 17.5a2 2 0 0 0 4 0",
    // Table lamp: shade, stem, foot.
    OutputIcon.LAMP to "M8 3h8l3 8H5z M12 11v8 M8 21h8",
    // A strip with LEDs.
    OutputIcon.STRIP to "M3 9h18v6H3z M7 12h0.01 M12 12h0.01 M17 12h0.01",
    // Wall socket: round face, two pins.
    OutputIcon.SOCKET to "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z M8.5 12h0.01 M15.5 12h0.01",
    // Fan: hub and three blades.
    OutputIcon.FAN to "M12 10a2 2 0 1 0 0 4a2 2 0 1 0 0-4z M12 10c0-4 1-7 4-7c2 0 2 3-1 6 " +
        "M13.7 13c3.5 2 5.5 4.5 4 7c-1 1.7-3.5 0-4.5-3.5 M10.3 13c-3.5 2-6.5 2.5-7.5 0c-0.8-2 2-3.5 6-2.5",
    // Radiator: body with fins and feet.
    OutputIcon.HEATER to "M4 5h16v13H4z M8 5v13 M12 5v13 M16 5v13 M6 21v-3 M18 21v-3",
    // Screen on a stand.
    OutputIcon.MEDIA to "M3 4h18v12H3z M12 16v4 M8 20h8",
)

private val cache = HashMap<OutputIcon, ImageVector>()

fun OutputIcon.vector(): ImageVector = cache.getOrPut(this) {
    ImageVector.Builder(name = id, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .addPath(
            pathData = addPathNodes(PATHS.getValue(this)),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
        .build()
}
