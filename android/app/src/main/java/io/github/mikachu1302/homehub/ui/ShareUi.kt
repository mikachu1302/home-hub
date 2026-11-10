package io.github.mikachu1302.homehub.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import io.github.mikachu1302.homehub.core.Household

/** The QR code for [link], or null when it does not fit into one code. */
private fun qrMatrix(link: String): BitMatrix? = try {
    QRCodeWriter().encode(
        link,
        BarcodeFormat.QR_CODE,
        0,
        0,
        mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 4),
    )
} catch (_: WriterException) {
    null
}

/** Shows the setup as a QR code. Dark on white even in dark mode: scanners need that contrast. */
@Composable
fun ShareDialog(link: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val matrix = remember(link) { qrMatrix(link) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share setup") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (matrix == null) {
                    Text("The setup is too large for one QR code. Use \"Copy link\" and send it instead.")
                } else {
                    Canvas(
                        Modifier.fillMaxWidth().aspectRatio(1f)
                            .background(Color.White, RoundedCornerShape(8.dp))
                            .semantics { contentDescription = "QR code with this phone's setup" },
                    ) {
                        val cell = size.width / matrix.width
                        for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
                            if (matrix[x, y]) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
                        }
                    }
                }
                Text(
                    "Scan it with the other phone's camera. It shares devices, names, rooms, icons and order. " +
                        "Each phone keeps its own view settings. No passwords are included.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = {
            TextButton(onClick = { copyToClipboard(context, link) }) { Text("Copy link") }
        },
    )
}

/** Asks before a received setup changes anything. */
@Composable
fun ImportDialog(shared: Household, localDeviceIds: Set<String>, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val outputs = shared.devices.sumOf { it.channels }
    val known = shared.devices.count { it.id in localDeviceIds }
    val new = shared.devices.size - known
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Import setup?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${shared.devices.size} devices with $outputs outputs ($known already here, $new new).")
                Text(
                    "For these devices the names, rooms, icons and order from the other phone replace the ones here. " +
                        "Devices only on this phone stay. Your own view settings stay as they are.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Import") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

fun copyToClipboard(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Home Hub setup", text))
}

fun readClipboard(context: Context): String? =
    context.getSystemService(ClipboardManager::class.java)?.primaryClip
        ?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
