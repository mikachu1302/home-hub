package io.github.mikachu1302.homehub.tv

import android.Manifest
import android.os.Build
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mikachu1302.homehub.ui.TButton
import io.github.mikachu1302.homehub.ui.TTextButton
import kotlinx.coroutines.delay

/**
 * The Android TV page: tv-remote's app, moved in (docs/DESIGN.md, docs/PROTOCOLS.md). The remote, or finding and
 * pairing a TV when none is paired; its settings and app shortcuts are in Settings > Android TV. Connects while the page is visible and the app is in
 * front, and closes the connection otherwise (tv-remote did that for the whole app). Keep-awake and theater mode
 * apply only here.
 */
@Composable
fun TvSection(vm: RemoteViewModel, onAddApp: () -> Unit) {
    val paired by vm.paired.collectAsStateWithLifecycle()
    val pairing by vm.pairing.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val adding by vm.addingTv.collectAsStateWithLifecycle()
    BackHandler(enabled = pairing != PairingUi.None) { vm.cancelPairing() }
    BackHandler(enabled = adding && pairing == PairingUi.None) { vm.cancelAddTv() }

    ConnectWhileVisible(vm)
    // Keep-awake and theater mode: Home Hub applies them on every control page (MainActivity, ui/ControlPage.kt).
    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalSettings provides settings) {
            val tv = paired
            when {
                pairing != PairingUi.None -> PairingScreen(pairing, vm)
                tv == null || adding -> FindTvScreen(vm, canCancel = tv != null)
                // Its settings and app shortcuts are in Settings > Android TV (Home Hub); "+" in the app row opens them.
                else -> RemoteScreen(tv, vm, onAddApp = onAddApp)
            }
        }
    }
}

/** Connected while this page is shown and the app is started; closed when the page or the app goes away. */
@Composable
internal fun ConnectWhileVisible(vm: RemoteViewModel) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> vm.onForeground() // no-op when not paired or already connecting
                Lifecycle.Event.ON_STOP -> vm.onBackground()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer) // delivers ON_START at once when the app is already started
        onDispose {
            lifecycle.removeObserver(observer)
            vm.onBackground()
        }
    }
}

// ---------------------------------------------------------------- finding and pairing (tv-remote's screens)

@Composable
private fun FindTvScreen(vm: RemoteViewModel, canCancel: Boolean) {
    val context = LocalContext.current
    val paired by vm.tvs.collectAsStateWithLifecycle()
    var allowed by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < 37 ||
                context.checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) == android.content.pm.PackageManager.PERMISSION_GRANTED,
        )
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
    LaunchedEffect(Unit) {
        if (!allowed && Build.VERSION.SDK_INT >= 37) ask.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
    }

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(if (canCancel) "Add a TV" else "Find your TV", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        if (!allowed) {
            Text("The app needs access to devices on your network to find the TV.")
            TButton(onClick = { if (Build.VERSION.SDK_INT >= 37) ask.launch(Manifest.permission.ACCESS_LOCAL_NETWORK) }) { Text("Allow") }
            return@Column
        }
        // Discovery runs only while this screen is open: the user's action (AGENTS.md).
        var tvs by remember { mutableStateOf(emptyList<Tv>()) }
        LaunchedEffect(Unit) { discoverTvs(context).collect { tvs = it } }
        if (tvs.isEmpty()) {
            Text("Searching on the Wi-Fi… Make sure the TV is on and on the same network.")
        } else {
            Text("Tap your TV to pair:")
            tvs.forEach { tv ->
                val already = paired.any { it.name == tv.name }
                TButton(onClick = { vm.startPairing(tv) }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Text(if (already) "${tv.name} (paired – pair again)" else tv.name)
                }
            }
        }
        if (canCancel) TTextButton(onClick = vm::cancelAddTv) { Text("Cancel") }
    }
}

@Composable
private fun PairingScreen(state: PairingUi, vm: RemoteViewModel) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Pairing", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        when (state) {
            is PairingUi.Starting -> Text("Contacting ${state.tv.name}…")
            is PairingUi.WaitingForCode -> {
                var code by remember { mutableStateOf("") }
                Text("Enter the 6-character code shown on the TV.")
                if (state.wrongCode) {
                    Text("That code was wrong. The TV shows a new one.", color = MaterialTheme.colorScheme.error)
                }
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.filter { c -> c.isLetterOrDigit() }.take(6).uppercase() },
                    label = { Text("Code") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TButton(
                    onClick = { vm.submitCode(code) },
                    enabled = code.length == 6,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) { Text("Pair") }
            }
            is PairingUi.Failed -> {
                Text("Pairing failed: ${state.message}", color = MaterialTheme.colorScheme.error)
            }
            PairingUi.None -> Unit
        }
        TTextButton(onClick = vm::cancelPairing) { Text("Cancel") }
    }
}
