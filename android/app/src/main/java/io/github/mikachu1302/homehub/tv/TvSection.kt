package io.github.mikachu1302.homehub.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mikachu1302.homehub.ui.BackKey
import io.github.mikachu1302.homehub.ui.Dimens
import io.github.mikachu1302.homehub.ui.LocalTerm
import io.github.mikachu1302.homehub.ui.PageHeader
import io.github.mikachu1302.homehub.ui.TButton
import io.github.mikachu1302.homehub.ui.TTextButton

/**
 * The Android TV page: tv-remote's app, moved in (docs/DESIGN.md, docs/PROTOCOLS.md). The remote, or finding and
 * pairing a TV when none is paired; its settings and app shortcuts are in Settings > Android TV. Connects while the page is visible and the app is in
 * front, and closes the connection otherwise (tv-remote did that for the whole app). Keep-awake and theater mode
 * come from ui/ControlPage, as on every control page.
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
                // [adding]: the user asked to add a TV (here or in Settings), so the search starts at once.
                tv == null || adding -> FindTvScreen(vm, adding = adding)
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

/** The page frame for finding and pairing: the usual header (its back key cancels like the system back), then the content. */
@Composable
private fun FindFrame(status: String, onCancel: (() -> Unit)?, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(start = Dimens.screenPadding, end = Dimens.screenPadding, top = Dimens.topPadding, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PageHeader("Android TV", status, leading = onCancel?.let { { BackKey(it) } })
        content()
    }
}

@Composable
private fun FindTvScreen(vm: RemoteViewModel, adding: Boolean) {
    val context = LocalContext.current
    val paired by vm.tvs.collectAsStateWithLifecycle()
    // Discovery runs only on the user's action (AGENTS.md): "Add a TV" was one; with nothing paired, "Find TVs" is.
    var searching by rememberSaveable(adding) { mutableStateOf(adding) }
    FindFrame(if (adding) "Add a TV" else "No stick paired yet", if (adding) vm::cancelAddTv else null) {
        if (!searching) {
            Text("Make sure the TV is on and on the same Wi-Fi, then look for it.")
            TButton(onClick = { searching = true }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Find TVs") }
            return@FindFrame
        }
        // The search stops when this screen goes away.
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
        if (adding) TTextButton(onClick = vm::cancelAddTv) { Text("Cancel") }
    }
}

@Composable
private fun PairingScreen(state: PairingUi, vm: RemoteViewModel) {
    val warn = LocalTerm.current.palette.warn
    FindFrame("Pairing", vm::cancelPairing) {
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
                Text("Pairing failed: ${state.message}", color = warn)
            }
            PairingUi.None -> Unit
        }
        TTextButton(onClick = vm::cancelPairing) { Text("Cancel") }
    }
}
