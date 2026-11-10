package io.github.mikachu1302.homehub.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DevicesScreen(vm: HomeViewModel) {
    val context = LocalContext.current
    var sharing by remember { mutableStateOf<String?>(null) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.screenPadding, vertical = Dimens.topPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.gap),
    ) {
        Text("Devices", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Button(
            onClick = { vm.discover() },
            enabled = !vm.discovering,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text(if (vm.discovering) "Searching…" else "Find devices") }
        Text(
            "Run this at home on your Wi-Fi. Found devices then also work away from home through the VPN. " +
                "Give them fixed addresses in the router so they stay found.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
            "Shelly · ${vm.devices.size} devices, ${vm.devices.sumOf { it.channels }} outputs",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        vm.devices.forEach { d ->
            Surface(
                shape = RoundedCornerShape(Dimens.corner),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(Dimens.rowPadding), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("${d.app} · ${d.channels} output${if (d.channels > 1) "s" else ""}", fontWeight = FontWeight.SemiBold)
                    Text(
                        d.address + " · " + (0 until d.channels).joinToString(" · ") { d.nameOf(it) },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Text(
            "IR bridge",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        Notice("Not set up yet. It is added here once the ATOM Lite runs (esphome/README.md).")

        Text(
            "Share with another phone",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.gap)) {
            Button(
                onClick = { sharing = vm.shareLink() },
                enabled = vm.devices.isNotEmpty(),
                modifier = Modifier.weight(1f).height(52.dp),
            ) { Text("Show QR code") }
            OutlinedButton(
                onClick = {
                    val text = readClipboard(context)
                    if (text.isNullOrBlank()) vm.message = "The clipboard is empty. Copy the setup link first."
                    else vm.offerImport(text)
                },
                modifier = Modifier.weight(1f).height(52.dp),
            ) { Text("Paste link") }
        }
        Text(
            "Shares devices, names, rooms, icons and order. Scan the code with the other phone's camera, " +
                "or copy the link and send it. Each phone keeps its own view settings.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    sharing?.let { link -> ShareDialog(link, onDismiss = { sharing = null }) }
}
