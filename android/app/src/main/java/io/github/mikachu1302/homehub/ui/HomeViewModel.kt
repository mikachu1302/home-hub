package io.github.mikachu1302.homehub.ui

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.mikachu1302.homehub.core.SavedDevice
import io.github.mikachu1302.homehub.core.Section
import io.github.mikachu1302.homehub.core.SwitchConfig
import io.github.mikachu1302.homehub.core.SwitchStatus
import io.github.mikachu1302.homehub.core.mergeDevices
import io.github.mikachu1302.homehub.data.Store
import io.github.mikachu1302.homehub.net.ShellyClient
import io.github.mikachu1302.homehub.net.findShellys
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** One switchable output: a device channel with what the device last reported. */
data class OutputUi(
    val device: SavedDevice,
    val channel: Int,
    val name: String,
    val status: SwitchStatus?,
    val config: SwitchConfig?,
    val reachable: Boolean,
    val busy: Boolean,
)

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)
    private val shelly = ShellyClient()

    var devices by mutableStateOf(store.devices)
        private set
    var section by mutableStateOf(store.lastSection)
        private set
    var backHint by mutableStateOf(false)
        private set
    var discovering by mutableStateOf(false)
        private set
    /** A short message for the user (a failed command); cleared when shown. */
    var message by mutableStateOf<String?>(null)

    private val statuses = mutableStateMapOf<String, List<SwitchStatus>>()
    private val configs = mutableStateMapOf<Pair<String, Int>, SwitchConfig>()
    private val unreachable = mutableStateMapOf<String, Boolean>()
    private val busy = mutableStateMapOf<Pair<String, Int>, Boolean>()

    /** True when devices are saved but none of them answered the last refresh (no Wi-Fi, VPN down, …). */
    val allUnreachable: Boolean
        get() = devices.isNotEmpty() && devices.all { unreachable[it.id] == true }

    // When the phone gets a network again (Wi-Fi back, VPN up), refresh at once instead of waiting for the next poll.
    private val connectivity = app.getSystemService(ConnectivityManager::class.java)
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            viewModelScope.launch { refresh() }
        }
    }

    init {
        runCatching { connectivity?.registerDefaultNetworkCallback(networkCallback) }
    }

    override fun onCleared() {
        runCatching { connectivity?.unregisterNetworkCallback(networkCallback) }
    }

    val outputs: List<OutputUi>
        get() = devices.flatMap { d ->
            (0 until d.channels).map { ch ->
                OutputUi(
                    device = d,
                    channel = ch,
                    name = d.nameOf(ch),
                    status = statuses[d.id]?.firstOrNull { it.channel == ch },
                    config = configs[d.id to ch],
                    reachable = unreachable[d.id] != true,
                    busy = busy[d.id to ch] == true,
                )
            }
        }

    fun open(target: Section) {
        section = target
        store.lastSection = target
        if (target != Section.MENU && !store.backHintShown) {
            store.backHintShown = true
            backHint = true
        }
    }

    fun back() = open(Section.MENU)

    fun hintShown() {
        backHint = false
    }

    /** Reads every device's switches; [withConfig] also reads the timers. Unreachable devices are marked, not removed. */
    suspend fun refresh(withConfig: Boolean = false) = coroutineScope {
        devices.map { d ->
            async {
                try {
                    statuses[d.id] = shelly.switches(d.address)
                    unreachable[d.id] = false
                    if (withConfig) {
                        for (ch in 0 until d.channels) configs[d.id to ch] = shelly.config(d.address, ch)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    unreachable[d.id] = true
                }
            }
        }.awaitAll()
    }

    /** The "Try now" button. */
    fun retryNow() {
        viewModelScope.launch { refresh(withConfig = true) }
    }

    /** Switches an output to the opposite of what it last reported, then shows what the device reports now. */
    fun toggle(o: OutputUi) {
        val target = !(o.status?.on ?: false)
        val key = o.device.id to o.channel
        if (busy[key] == true) return
        busy[key] = true
        viewModelScope.launch {
            try {
                shelly.set(o.device.address, o.channel, target)
                statuses[o.device.id] = shelly.switches(o.device.address)
                unreachable[o.device.id] = false
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                unreachable[o.device.id] = true
                message = "Could not reach ${o.name}"
            } finally {
                busy[key] = false
            }
        }
    }

    /** Saves the name locally and the timers on the device, then reads the timers back. */
    fun save(o: OutputUi, name: String, config: SwitchConfig?) {
        val trimmed = name.trim()
        devices = devices.map { d ->
            if (d.id != o.device.id) d
            else d.copy(names = d.names + (o.channel to trimmed))
        }
        store.devices = devices
        if (config == null || config == o.config) return
        viewModelScope.launch {
            try {
                shelly.setConfig(o.device.address, config)
                configs[o.device.id to o.channel] = shelly.config(o.device.address, o.channel)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = "Timer not saved: ${e.message ?: "device not reachable"}"
            }
        }
    }

    fun discover() {
        if (discovering) return
        discovering = true
        viewModelScope.launch {
            try {
                val found = findShellys(getApplication())
                devices = mergeDevices(devices, found)
                store.devices = devices
                message = if (found.isEmpty()) "No Shelly found. Are you on the home Wi-Fi?" else "Found ${found.size} Shelly devices"
                refresh(withConfig = true)
            } finally {
                discovering = false
            }
        }
    }
}
