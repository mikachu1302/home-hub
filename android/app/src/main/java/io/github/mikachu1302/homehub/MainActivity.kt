package io.github.mikachu1302.homehub

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.viewModels
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.mikachu1302.homehub.core.Section
import io.github.mikachu1302.homehub.ui.AmpScreen
import io.github.mikachu1302.homehub.ui.CustomPageScreen
import io.github.mikachu1302.homehub.ui.HdmiScreen
import io.github.mikachu1302.homehub.ui.TvIrScreen
import io.github.mikachu1302.homehub.ui.Dimens
import io.github.mikachu1302.homehub.ui.ControlPage
import io.github.mikachu1302.homehub.ui.HomeHubTheme
import io.github.mikachu1302.homehub.ui.LocalBack
import io.github.mikachu1302.homehub.ui.LocalHaptics
import io.github.mikachu1302.homehub.ui.LocalTerm
import io.github.mikachu1302.homehub.ui.Palettes
import io.github.mikachu1302.homehub.ui.SPLASH_THEMES
import io.github.mikachu1302.homehub.ui.TButton
import io.github.mikachu1302.homehub.ui.crtOverlay
import io.github.mikachu1302.homehub.ui.termBackground
import io.github.mikachu1302.homehub.ui.HomeViewModel
import io.github.mikachu1302.homehub.ui.ImportDialog
import io.github.mikachu1302.homehub.ui.MenuScreen
import io.github.mikachu1302.homehub.ui.SettingsScreen
import io.github.mikachu1302.homehub.ui.SwitchesScreen
import io.github.mikachu1302.homehub.tv.RemoteViewModel
import io.github.mikachu1302.homehub.tv.TvSection

class MainActivity : ComponentActivity() {
    private val vm: HomeViewModel by viewModels()

    /** tv-remote's view model (the Android TV page and its blocks): paired TVs, the connection, TV state, voice, its settings. */
    private val tvVm: RemoteViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Every palette is dark: light system-bar icons always, whatever the phone's own theme.
        enableEdgeToEdge(SystemBarStyle.dark(AndroidColor.TRANSPARENT), SystemBarStyle.dark(AndroidColor.TRANSPARENT))
        if (savedInstanceState == null) handleLink(intent)
        setContent {
            val look = vm.appearance
            // The next start's launch screen shows this palette's icon on its background (Android 13+ remembers it).
            LaunchedEffect(look.palette) {
                if (Build.VERSION.SDK_INT >= 33) {
                    SPLASH_THEMES[Palettes.byId(look.palette).id]?.let { runCatching { splashScreen.setSplashScreenTheme(it) } }
                }
            }
            HomeHubTheme(look.style, Palettes.byId(look.palette), look.scanlines) {
                val term = LocalTerm.current
                // The Surface sets the content colour (the palette's text) for everything inside, as in tv-remote.
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .termBackground()
                            .let { if (term.crt && term.scanlines) it.crtOverlay() else it },
                    ) { App(vm, tvVm) }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleLink(intent)
    }

    /** A shared setup link (docs/SHARING.md) opened from the camera; nothing is applied before the user confirms. */
    private fun handleLink(intent: Intent?) {
        val data = intent?.data ?: return
        if (intent.action == Intent.ACTION_VIEW && data.scheme == "homehub") vm.offerImport(data.toString())
    }
}

@Composable
private fun App(vm: HomeViewModel, tvVm: RemoteViewModel) {
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

    vm.pendingImport?.let { shared ->
        ImportDialog(
            setup = shared,
            localDeviceIds = vm.devices.map { it.id }.toSet(),
            onConfirm = {
                vm.confirmImport(it)
                tvVm.reloadShared() // the TV for volume, remote settings and app shortcuts are in tv-remote's file
            },
            onCancel = vm::cancelImport,
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = Color.Transparent,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            // Settings > Remote (stored with tv-remote's settings): haptics for every key, keep-awake and theater
            // mode on the control pages.
            val remote by tvVm.settings.collectAsStateWithLifecycle()
            val control = vm.section in setOf(Section.AMP, Section.TV, Section.TV_IR, Section.HDMI, Section.PAGE)
            // The small back key in page headers: on every page below the menu (Settings has its own).
            val back: (() -> Unit)? = if (vm.section == Section.MENU) null else vm::back
            CompositionLocalProvider(LocalHaptics provides remote.haptics, LocalBack provides back) {
                LocalNetworkGate {
                    ControlPage(keepAwake = control && remote.keepAwake, theater = control && remote.theater && !vm.pageEditing) {
                        when (vm.section) {
                            Section.MENU -> MenuScreen(vm)
                            Section.SWITCHES -> SwitchesScreen(vm)
                            Section.AMP -> AmpScreen(vm.title(Section.AMP), vm.irBridge.isNotBlank(), vm.irStatus, vm::press)
                            Section.TV -> TvSection(tvVm, onAddApp = { vm.openSettings("AppShortcuts", Section.TV) })
                            Section.TV_IR -> TvIrScreen(vm.title(Section.TV_IR), vm.irBridge.isNotBlank(), vm.irStatus, vm::press) {
                                // Wake-on-LAN to the TV set up in Settings > TV: its MAC address.
                                vm.message = if (tvVm.wakeTv()) "Wake signal sent (works when the TV is fully in standby)"
                                else "Set the TV's MAC address in Settings > TV"
                            }
                            Section.PAGE -> CustomPageScreen(vm)
                            Section.HDMI -> HdmiScreen(vm.title(Section.HDMI), vm.irBridge.isNotBlank(), vm.irStatus, vm::press)
                            Section.SETTINGS -> SettingsScreen(vm)
                        }
                    }
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
            Text("The app needs access to devices on your network to reach the switches and the IR Bridge.")
            TButton(onClick = { if (Build.VERSION.SDK_INT >= 37) ask.launch(localNetworkPermission()) }) { Text("Allow") }
        }
    }
}

@RequiresApi(37)
private fun localNetworkPermission() = Manifest.permission.ACCESS_LOCAL_NETWORK
