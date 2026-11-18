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
import io.github.mikachu1302.homehub.core.Appearance
import io.github.mikachu1302.homehub.core.CustomPage
import io.github.mikachu1302.homehub.core.SwitchLayout
import io.github.mikachu1302.homehub.core.PAGE_PREFIX
import io.github.mikachu1302.homehub.core.menuIds
import io.github.mikachu1302.homehub.core.newPageId
import io.github.mikachu1302.homehub.core.newActionId
import io.github.mikachu1302.homehub.core.Action
import io.github.mikachu1302.homehub.core.ActionTargets
import io.github.mikachu1302.homehub.core.StepKind
import io.github.mikachu1302.homehub.core.irKeyOf
import io.github.mikachu1302.homehub.core.stepTitle
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import io.github.mikachu1302.homehub.core.pageMenuId
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
import kotlinx.coroutines.Dispatchers
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

/**
 * What happened to the last IR key. "Sent" means the bridge accepted it and fires the LED; whether the amp or TV saw
 * the light is unknowable (IR is one-way). [ms] counts from the tap, so waiting behind earlier presses shows too.
 */
/** An action running or done on the custom page [pageId] (shown in its header); [done] with [failed] steps. */
data class ActionRun(val pageId: String, val actionId: String, val text: String, val done: Boolean, val failed: Boolean = false)

sealed interface IrStatus {
    val key: IrKey

    data class Sending(override val key: IrKey) : IrStatus
    data class Sent(override val key: IrKey, val ms: Long) : IrStatus
    data class Failed(override val key: IrKey, val reason: String) : IrStatus
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
    var switchLayout by mutableStateOf(store.switchLayout)
        private set
    var menuOrder by mutableStateOf(store.menuOrder)
        private set
    var menuNames by mutableStateOf(store.menuNames)
        private set

    /** Look and colours (a preference, docs/SHARING.md). */
    var appearance by mutableStateOf(store.appearance)
        private set

    /** The user's own pages (a preference), and the one that is open when [section] is [Section.PAGE]. */
    var pages by mutableStateOf(store.pages)
        private set
    var pageId by mutableStateOf(store.lastPage)
        private set

    /** The open custom page, or null (also when it was deleted, for example by an import). */
    val page: CustomPage?
        get() = pages.firstOrNull { it.id == pageId }
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

    /** The menu in the user's order: built-in sections (`Section` names) and custom pages (`page:<id>`). */
    val menu: List<String>
        get() = menuIds(menuOrder, pages)

    /** The title the user gave [section] (menu entry and page title), or its default. */
    fun title(section: Section): String = sectionTitle(section, menuNames)

    /** The title of a menu id. */
    fun menuTitle(id: String): String =
        pages.firstOrNull { pageMenuId(it) == id }?.title ?: runCatching { title(Section.valueOf(id)) }.getOrDefault(id)

    fun moveMenu(from: Int, delta: Int) {
        menuOrder = moved(menu, from, delta)
        store.menuOrder = menuOrder
    }

    /** Opens a menu entry: a built-in section or a custom page. */
    fun openMenu(id: String) {
        val p = pages.firstOrNull { pageMenuId(it) == id }
        if (p != null) openPage(p.id) else runCatching { Section.valueOf(id) }.getOrNull()?.let(::open)
    }

    fun openPage(id: String) {
        pageId = id
        store.lastPage = id
        open(Section.PAGE)
    }

    /** Adds an empty page at the end of the menu and returns it. */
    fun newPage(): CustomPage {
        val p = CustomPage(newPageId(pages) { kotlin.random.Random.nextLong() }, "My page")
        savePages(pages + p)
        return p
    }

    fun updatePage(page: CustomPage) = savePages(pages.map { if (it.id == page.id) page else it })

    fun deletePage(id: String) {
        savePages(pages.filter { it.id != id })
        menuOrder = menuOrder - (PAGE_PREFIX + id)
        store.menuOrder = menuOrder
        if (section == Section.PAGE && pageId == id) back()
    }

    private fun savePages(list: List<CustomPage>) {
        pages = list
        store.pages = list
    }

    /** A blank name goes back to the default title. */
    fun renameSection(section: Section, name: String) {
        menuNames = if (name.isBlank()) menuNames - section.name else menuNames + (section.name to name.trim())
        store.menuNames = menuNames
    }

    fun updateAppearance(change: (Appearance) -> Appearance) {
        appearance = change(appearance)
        store.appearance = appearance
    }

    fun showLayout(layout: SwitchLayout) {
        switchLayout = layout
        store.switchLayout = layout
    }

    fun showView(view: SwitchView) {
        switchView = view
        store.switchView = view
    }

    fun open(target: Section) {
        // The IR send status belongs to the page the key was sent from: a new page starts without one.
        if (target != section || target == Section.PAGE) {
            irStatus = null
            actionRun = null
        }
        section = target
        store.lastSection = target
        if (target != Section.MENU && !store.backHintShown) {
            store.backHintShown = true
            backHint = true
        }
    }

    fun back() = open(Section.MENU)

    /** A Settings page another page asked for (its name) and where back from it returns to; read once by Settings. */
    var settingsEntry by mutableStateOf<Pair<String, Section>?>(null)

    /** A custom page is in edit mode: MainActivity turns theater mode off meanwhile. */
    var pageEditing by mutableStateOf(false)

    /** Opens Settings at [page] (a page name); back from it returns to [from]. */
    fun openSettings(page: String, from: Section) {
        settingsEntry = page to from
        open(Section.SETTINGS)
    }

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

    /** [takePreferences]: also use the sender's preferences (list view, menu, look and colours). */
    fun confirmImport(takePreferences: Boolean) {
        val shared = pendingImport ?: return
        pendingImport = null
        if (takePreferences) {
            shared.preferences?.let { p ->
                store.preferences = p
                switchView = p.switchView
                menuOrder = p.menuOrder
                menuNames = p.menuNames
                appearance = p.appearance
                pages = p.pages ?: pages // a link from before custom pages keeps this phone's pages
                switchLayout = p.switchLayout
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

    /** The last IR key's fate, shown on the page that sent it (Amplifier, TV, HDMI Switch, custom pages). */
    var irStatus by mutableStateOf<IrStatus?>(null)
        private set

    /**
     * Sends one IR key. The amp reports nothing back, so a success shows nothing; a failure says why.
     * [repeat]: a repeat while the key is held; dropped while a press is still on its way, so a slow network never
     * queues up steps that keep the volume moving after the key is let go.
     */
    fun press(key: IrKey, repeat: Boolean = false) {
        if (irBridge.isBlank() || (repeat && pressesInFlight > 0)) return
        pressesInFlight++
        val start = System.nanoTime()
        // The result shows only on the page that sent the key (open() clears the status on a page change).
        val from = section to pageId
        fun report(status: IrStatus) {
            if ((section to pageId) == from) irStatus = status
        }
        irStatus = IrStatus.Sending(key)
        viewModelScope.launch {
            try {
                bridge.press(irBridge, bridgeLogin, key)
                report(IrStatus.Sent(key, (System.nanoTime() - start) / 1_000_000))
            } catch (e: CancellationException) {
                throw e
            } catch (e: BridgeException) {
                report(IrStatus.Failed(key, e.message ?: "the bridge refused it"))
            } catch (_: Exception) {
                report(IrStatus.Failed(key, "the bridge did not answer (Wi-Fi, power or VPN?)"))
            } finally {
                pressesInFlight--
            }
        }
    }

    // --- actions (core/Actions.kt) ---

    /** The action running or last run, for the header of the page it belongs to. */
    var actionRun by mutableStateOf<ActionRun?>(null)
        private set
    private var actionJob: Job? = null

    /** An id for a new action block. */
    fun newActionId(): String = newActionId(pages) { kotlin.random.Random.nextLong() }

    /**
     * Runs [action]'s steps in order. A failed step does not stop the others (an amp that does not answer should not
     * keep the TV off); the header says which failed. Runs on when the page is left; one action at a time.
     */
    fun runAction(action: Action, tv: ActionTargets) {
        if (actionJob?.isActive == true) {
            message = "An action is still running"
            return
        }
        val page = pageId
        val names = { key: String -> outputs.firstOrNull { it.key == key }?.name }
        actionJob = viewModelScope.launch {
            val failures = mutableListOf<String>()
            action.steps.forEachIndexed { i, step ->
                val title = stepTitle(step, names)
                actionRun = ActionRun(page, action.id, "${action.name}: ${i + 1}/${action.steps.size} $title…", done = false)
                val error: String? = try {
                    when (step.kind) {
                        StepKind.IR -> if (irBridge.isBlank()) "IR Bridge not set up" else {
                            bridge.press(irBridge, bridgeLogin, irKeyOf(step))
                            delay(300) // the next key right after can get lost
                            null
                        }
                        StepKind.WAIT -> {
                            delay(step.seconds * 1_000L)
                            null
                        }
                        StepKind.SWITCH_ON, StepKind.SWITCH_OFF -> {
                            val o = outputs.firstOrNull { it.key == step.output }
                            if (o == null) "switch removed" else {
                                shelly.set(o.device.address, o.channel, step.kind == StepKind.SWITCH_ON)
                                // Show what the device reports now, not what was asked for (AGENTS.md).
                                statuses[o.device.id] = shelly.switches(o.device.address)
                                null
                            }
                        }
                        StepKind.STICK_ON, StepKind.STICK_OFF -> tv.stickPower(step.kind == StepKind.STICK_ON)
                        StepKind.TV_WAKE -> if (tv.wakeTv()) null else "no TV MAC address (Settings > TV)"
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: BridgeException) {
                    e.message ?: "the bridge refused it"
                } catch (_: Exception) {
                    "no answer"
                }
                if (error != null) failures += "$title: $error"
            }
            actionRun = ActionRun(
                page, action.id,
                when {
                    failures.isEmpty() -> "✓ ${action.name} sent"
                    failures.size == 1 -> "✗ ${action.name}: ${failures[0]}"
                    else -> "✗ ${action.name}: ${failures[0]} (+${failures.size - 1} more)"
                },
                done = true, failed = failures.isNotEmpty(),
            )
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
