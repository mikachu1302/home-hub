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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import io.github.mikachu1302.homehub.core.SharedSetup
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role

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

/**
 * Share dialog: the sender ticks what goes into the code; the QR code and "Copy link" follow the ticks.
 * Dark on white even in dark mode: scanners need that contrast.
 */
@Composable
fun ShareDialog(
    linkFor: (household: Boolean, preferences: Boolean, passwords: Boolean) -> String?,
    hasPasswords: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var household by remember { mutableStateOf(true) }
    var preferences by remember { mutableStateOf(true) }
    // Off every time the dialog opens; never remembered (docs/SHARING.md).
    var passwords by remember { mutableStateOf(false) }
    val link = remember(household, preferences, passwords) { linkFor(household, preferences, passwords) }
    val matrix = remember(link) { link?.let(::qrMatrix) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share setup") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CheckRow("Devices, names, rooms, icons, order", household) { household = it }
                CheckRow("My preferences (menu, pages, look)", preferences) { preferences = it }
                if (hasPasswords) {
                    CheckRow("IR Bridge password", passwords) { passwords = it }
                    if (passwords) {
                        Text(
                            "Anyone who sees this code can read the password.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                when {
                    link == null -> Text("Tick at least one.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    matrix == null -> Text("Too large for a QR code. Use \"Copy link\".")
                    else -> Canvas(
                        Modifier.fillMaxWidth().aspectRatio(1f)
                            .background(Color.White, RoundedCornerShape(8.dp))
                            .semantics { contentDescription = "QR code with the ticked parts of this phone's setup" },
                    ) {
                        val cell = size.width / matrix.width
                        for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
                            if (matrix[x, y]) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
                        }
                    }
                }
                Text("Scan with the other phone's camera.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TTextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = {
            TTextButton(onClick = { link?.let { copyToClipboard(context, it) } }, enabled = link != null) { Text("Copy link") }
        },
    )
}

/** Asks before a received setup changes anything; shows only the parts the code carries. */
@Composable
fun ImportDialog(setup: SharedSetup, localDeviceIds: Set<String>, onConfirm: (takePreferences: Boolean) -> Unit, onCancel: () -> Unit) {
    val shared = setup.household
    var takePreferences by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Import setup?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (shared != null) {
                    val outputs = shared.devices.sumOf { it.channels }
                    val known = shared.devices.count { it.id in localDeviceIds }
                    Text("${shared.devices.size} devices, $outputs outputs ($known known, ${shared.devices.size - known} new).")
                    Text(
                        "Their names, rooms and order replace the ones here.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (shared != null && shared.irBridge.isNotBlank()) {
                    Text("IR Bridge: ${shared.irBridge}", fontSize = 13.sp)
                }
                setup.secrets?.irBridge?.let {
                    Text("IR Bridge password (stored encrypted).")
                }
                if (setup.preferences != null) {
                    if (shared != null) {
                        CheckRow("Also take their preferences", takePreferences) { takePreferences = it }
                    } else {
                        Text("Their preferences replace yours.")
                    }
                }
            }
        },
        confirmButton = {
            TTextButton(onClick = { onConfirm(setup.preferences != null && (takePreferences || shared == null)) }) { Text("Import") }
        },
        dismissButton = { TTextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(label, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp))
    }
}

fun copyToClipboard(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Home Hub setup", text))
}

fun readClipboard(context: Context): String? =
    context.getSystemService(ClipboardManager::class.java)?.primaryClip
        ?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
