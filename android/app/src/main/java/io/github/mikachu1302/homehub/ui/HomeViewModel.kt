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
import io.github.mikachu1302.homehub.core.AmpKeys
import io.github.mikachu1302.homehub.core.BridgeLogin
import io.github.mikachu1302.homehub.core.IrKey
import io.github.mikachu1302.homehub.core.Secrets
import io.github.mikachu1302.homehub.core.OutputIcon
import io.github.mikachu1302.homehub.core.OutputPrefs
import io.github.mikachu1302.homehub.core.SavedDevice
import io.github.mikachu1302.homehub.core.Section
import io.github.mikachu1302.homehub.core.SetupShare
import io.github.mikachu1302.homehub.core.SharedSetup
import io.github.mikachu1302.homehub.core.ShareException
import io.github.mikachu1302.homehub.core.SwitchConfig
import io.github.mikachu1302.homehub.core.SwitchStatus
import io.github.mikachu1302.homehub.core.SwitchView
import io.github.mikachu1302.homehub.core.RoomGroup
import io.github.mikachu1302.homehub.core.groupByRoom
import io.github.mikachu1302.homehub.core.mergeDevices
import io.github.mikachu1302.homehub.core.menuSections
import io.github.mikachu1302.homehub.core.sectionTitle
import io.github.mikachu1302.homehub.core.moved
import io.github.mikachu1302.homehub.core.outputKey
import io.github.mikachu1302.homehub.core.sortByOrder
import io.github.mikachu1302.homehub.core.roomsInUse
import io.github.mikachu1302.homehub.data.SecretStore
import io.github.mikachu1302.homehub.data.Store
import io.github.mikachu1302.homehub.net.BridgeException
import io.github.mikachu1302.homehub.net.IrBridgeClient
import io.github.mikachu1302.homehub.net.NotLanException
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
    /** Blank when no room is set. */
    val room: String,
    val icon: OutputIcon,
    val status: SwitchStatus?,
    val config: SwitchConfig?,
    val reachable: Boolean,
    val busy: Boolean,
) {
    val key: String get() = outputKey(device.id, channel)
}

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)
    private val shelly = ShellyClient()
    private val secrets = SecretStore(app)
    private val bridge = IrBridgeClient()

    var devices by mutableStateOf(store.devices)
        private set
    var section by mutableStateOf(store.lastSection)
        private set
    var backHint by mutableStateOf(false)
        private set
    var discovering by mutableStateOf(false)
        private set
    var switchView by mutableStateOf(store.switchView)
        private set
    var menuOrder by mutableStateOf(store.menuOrder)
        private set
    var menuNames by mutableStateOf(store.menuNames)
        private set
    /** The IR bridge's address (blank = not set up) and its login (from the Keystore). */
    var irBridge by mutableStateOf(store.irBridge)
        private set
    var bridgeLogin by mutableStateOf(secrets.bridgeLogin)
        private set
    /** The result of the last check (true = it works) and its text; null = not checked since the last change. */
    var bridgeCheck by mutableStateOf<Pair<Boolean, String>?>(null)
        private set
    var checkingBridge by mutableStateOf(false)
        private set
    private var outputOrder by mutableStateOf(store.outputOrder)
    private var roomOrder by mutableStateOf(store.roomOrder)
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

    /** All outputs in the user's order. */
    val outputs: List<OutputUi>
        get() = sortByOrder(unsortedOutputs, { it.key }, outputOrder)

    /** The outputs grouped by room, rooms in the user's order. */
    val roomGroups: List<RoomGroup<OutputUi>>
        get() = groupByRoom(outputs, { it.room }, roomOrder)

    private val unsortedOutputs: List<OutputUi>
        get() = devices.flatMap { d ->
            (0 until d.channels).map { ch ->
                OutputUi(
                    device = d,
                    channel = ch,
                    name = d.nameOf(ch),
                    room = d.roomOf(ch),
                    icon = d.iconOf(ch),
                    status = statuses[d.id]?.firstOrNull { it.channel == ch },
                    config = configs[d.id to ch],
                    reachable = unreachable[d.id] != true,
                    busy = busy[d.id to ch] == true,
                )
            }
        }

    /** Rooms already used somewhere, offered when setting another output's room. */
    val rooms: List<String>
        get() = roomsInUse(devices)

    /** The menu sections in the user's order. */
    val menu: List<Section>
        get() = menuSections(menuOrder)

    /** The title the user gave [section] (menu entry and page title), or its default. */
    fun title(section: Section): String = sectionTitle(section, menuNames)

    fun moveMenu(from: Int, delta: Int) {
        menuOrder = moved(menu, from, delta).map { it.name }
        store.menuOrder = menuOrder
    }

    /** A blank name goes back to the default title. */
    fun renameSection(section: Section, name: String) {
        menuNames = if (name.isBlank()) menuNames - section.name else menuNames + (section.name to name.trim())
        store.menuNames = menuNames
    }

    fun showView(view: SwitchView) {
        switchView = view
        store.switchView = view
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

    /** Moves an output inside its room ("By room" view). The new overall order is the grouped order. */
    fun moveInRoom(room: String?, from: Int, delta: Int) {
        val groups = roomGroups.map { g -> if (g.room == room) g.copy(items = moved(g.items, from, delta)) else g }
        saveOutputOrder(groups.flatMap { g -> g.items.map { it.key } })
    }

    /** Moves an output in the flat list ("All" view). */
    fun moveInAll(from: Int, delta: Int) = saveOutputOrder(moved(outputs, from, delta).map { it.key })

    /** Moves a room; "Other" (no room) always stays last. */
    fun moveRoom(from: Int, delta: Int) {
        roomOrder = moved(roomGroups.mapNotNull { it.room }, from, delta)
        store.roomOrder = roomOrder
    }

    private fun saveOutputOrder(order: List<String>) {
        outputOrder = order
        store.outputOrder = order
    }

    // --- sharing the setup (docs/SHARING.md) ---

    /** A received setup waiting for the user's confirmation. */
    var pendingImport by mutableStateOf<SharedSetup?>(null)
        private set

    /** The link for the QR code with the parts the sender ticked; null when nothing is ticked. */
    fun shareLink(household: Boolean, preferences: Boolean, passwords: Boolean): String? {
        val secret = if (passwords) bridgeLogin?.let { Secrets(irBridge = it) } else null
        if (!household && !preferences && secret == null) return null
        return SetupShare.toLink(
            SharedSetup(if (household) store.household else null, if (preferences) store.preferences else null, secret),
        )
    }

    /** A link from the camera, a messenger or the clipboard; parsed now, applied only after confirmation. */
    fun offerImport(link: String) {
        try {
            pendingImport = SetupShare.fromLink(link)
        } catch (e: ShareException) {
            message = e.message
        }
    }

    fun cancelImport() {
        pendingImport = null
    }

    /** [takePreferences]: also use the sender's preferences (list view, later theme …). */
    fun confirmImport(takePreferences: Boolean) {
        val shared = pendingImport ?: return
        pendingImport = null
        if (takePreferences) {
            shared.preferences?.let { p ->
                store.preferences = p
                switchView = p.switchView
                menuOrder = p.menuOrder
                menuNames = p.menuNames
            }
        }
        shared.secrets?.irBridge?.let { login ->
            secrets.bridgeLogin = login
            bridgeLogin = login
            bridgeCheck = null
        }
        shared.household?.let { h ->
            val merged = SetupShare.merge(store.household, h)
            store.household = merged
            devices = merged.devices
            outputOrder = merged.outputOrder
            roomOrder = merged.roomOrder
            if (merged.irBridge != irBridge) bridgeCheck = null
            irBridge = merged.irBridge
            viewModelScope.launch { refresh(withConfig = true) }
        }
        message = "Setup imported"
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

    /** Saves name and room on this phone and the timers on the device, then reads the timers back. */
    fun save(o: OutputUi, name: String, room: String, icon: OutputIcon, config: SwitchConfig?) {
        // A room typed with other capitals or spaces joins the existing one.
        val cleanRoom = room.trim().let { r -> rooms.firstOrNull { it.equals(r, ignoreCase = true) } ?: r }
        devices = devices.map { d ->
            if (d.id != o.device.id) d
            else d.withPrefs(o.channel, OutputPrefs(name = name.trim(), room = cleanRoom, icon = icon.id))
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

    // --- IR bridge (docs/PROTOCOLS.md) ---

    /** Saves address and login; a blank user removes the login. */
    fun saveBridge(address: String, user: String, password: String) {
        irBridge = address.trim()
        store.irBridge = irBridge
        bridgeLogin = if (user.isBlank()) null else BridgeLogin(user.trim(), password)
        secrets.bridgeLogin = bridgeLogin
        bridgeCheck = null
        if (irBridge.isNotEmpty()) checkBridge()
    }

    /** Checks the saved address and login. */
    fun checkBridge() = checkBridge(irBridge, bridgeLogin)

    /** Checks typed values before saving them (the setup dialog). */
    fun checkBridge(address: String, user: String, password: String) =
        checkBridge(address.trim(), if (user.isBlank()) null else BridgeLogin(user.trim(), password))

    /** Reaches the bridge with the login and looks up one key; sends nothing to the amp. */
    private fun checkBridge(address: String, login: BridgeLogin?) {
        if (checkingBridge) return
        if (address.isBlank()) {
            bridgeCheck = false to "Enter the bridge's address."
            return
        }
        checkingBridge = true
        bridgeCheck = null
        viewModelScope.launch {
            bridgeCheck = try {
                bridge.check(address, login, AmpKeys.volumeUp)
                true to "Connected. The login works."
            } catch (e: CancellationException) {
                throw e
            } catch (e: BridgeException) {
                false to (e.message ?: "The bridge refused the request.")
            } catch (_: NotLanException) {
                false to "$address is not an address on your home network."
            } catch (_: Exception) {
                false to "Can't reach the bridge at $address. Is it plugged in and on the Wi-Fi? Away from home, is the VPN on?"
            } finally {
                checkingBridge = false
            }
        }
    }

    private var pressesInFlight = 0

    /**
     * Sends one IR key. The amp reports nothing back, so a success shows nothing; a failure says why.
     * [repeat]: a repeat while the key is held; dropped while a press is still on its way, so a slow network never
     * queues up steps that keep the volume moving after the key is let go.
     */
    fun press(key: IrKey, repeat: Boolean = false) {
        if (irBridge.isBlank() || (repeat && pressesInFlight > 0)) return
        pressesInFlight++
        viewModelScope.launch {
            try {
                bridge.press(irBridge, bridgeLogin, key)
            } catch (e: CancellationException) {
                throw e
            } catch (e: BridgeException) {
                message = e.message
            } catch (_: Exception) {
                message = "Could not reach the IR bridge"
            } finally {
                pressesInFlight--
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
