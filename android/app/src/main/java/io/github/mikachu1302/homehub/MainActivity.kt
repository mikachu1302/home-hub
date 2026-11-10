package io.github.mikachu1302.homehub

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.mikachu1302.homehub.core.Section
import io.github.mikachu1302.homehub.ui.AmpScreen
import io.github.mikachu1302.homehub.ui.Dimens
import io.github.mikachu1302.homehub.ui.DevicesScreen
import io.github.mikachu1302.homehub.ui.HomeHubTheme
import io.github.mikachu1302.homehub.ui.HomeViewModel
import io.github.mikachu1302.homehub.ui.MenuScreen
import io.github.mikachu1302.homehub.ui.SwitchesScreen
import io.github.mikachu1302.homehub.ui.TvScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HomeHubTheme { App() }
        }
    }
}

@Composable
private fun App(vm: HomeViewModel = viewModel()) {
    val snackbar = remember { SnackbarHostState() }

    // Back is the only way to the menu, the same on every page (docs/PLAN.md, "App layout").
    BackHandler(enabled = vm.section != Section.MENU) { vm.back() }

    LaunchedEffect(vm.backHint) {
        if (vm.backHint) {
            snackbar.showSnackbar("Swipe in from the screen edge (or press back) to return to the menu")
            vm.hintShown()
        }
    }
    LaunchedEffect(vm.message) {
        vm.message?.let {
            vm.message = null
            snackbar.showSnackbar(it)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LocalNetworkGate {
                when (vm.section) {
                    Section.MENU -> MenuScreen(vm)
                    Section.SWITCHES -> SwitchesScreen(vm)
                    Section.AMP -> AmpScreen()
                    Section.TV -> TvScreen()
                    Section.DEVICES -> DevicesScreen(vm)
                }
            }
        }
    }
}

/** Android 17 made local-network access a runtime permission; nothing works without it. */
@Composable
private fun LocalNetworkGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var allowed by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < 37 ||
                context.checkSelfPermission(localNetworkPermission()) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
    LaunchedEffect(Unit) { if (!allowed && Build.VERSION.SDK_INT >= 37) ask.launch(localNetworkPermission()) }
    if (allowed) {
        content()
    } else {
        Column(Modifier.padding(Dimens.screenPadding), verticalArrangement = Arrangement.spacedBy(Dimens.gap)) {
            Text("Home Hub", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("The app needs access to devices on your network to reach the switches and the IR bridge.")
            Button(onClick = { if (Build.VERSION.SDK_INT >= 37) ask.launch(localNetworkPermission()) }) { Text("Allow") }
        }
    }
}

@RequiresApi(37)
private fun localNetworkPermission() = Manifest.permission.ACCESS_LOCAL_NETWORK
