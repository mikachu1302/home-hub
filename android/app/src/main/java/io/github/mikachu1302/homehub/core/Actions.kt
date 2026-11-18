package io.github.mikachu1302.homehub.core

/**
 * Actions: one key that runs several steps in order, for example "stick on, TV on, amp on" (docs/DESIGN.md,
 * "Actions"). An action lives in a custom page as an [BlockKind.ACTION] block. [StepKind.id] is stored and shared, so
 * it never changes; a step an older app does not know is dropped on reading.
 */
enum class StepKind(val id: String) {
    /** The Android TV stick on: nothing when it already is (the app knows its power state). */
    STICK_ON("stick_on"),
    STICK_OFF("stick_off"),
    /** The TV on by Wake-on-LAN (Settings > TV); a real "on", but only from deep standby (docs/PROTOCOLS.md). */
    TV_WAKE("tv_wake"),
    /** One key on the IR bridge, by its button name. The amp's and TV's power keys toggle. */
    IR("ir"),
    SWITCH_ON("switch_on"),
    SWITCH_OFF("switch_off"),
    WAIT("wait"),
    ;

    companion object {
        fun byId(id: String): StepKind? = entries.firstOrNull { it.id == id }
    }
}

/** One step. [key]: the bridge button name ([StepKind.IR]); [output]: an output key (switch steps); [seconds]: [StepKind.WAIT]. */
data class Step(val kind: StepKind, val key: String = "", val output: String = "", val seconds: Int = 0)

/** A named list of steps; [id] is made once and never changes. */
data class Action(val id: String, val name: String, val steps: List<Step> = emptyList())

/** A wait lasts 1 to 60 seconds. */
val WAIT_SECONDS = 1..60

/** The IR keys an action can press, with the name the editor shows ("toggle": the key switches on and off). */
val ACTION_IR_KEYS: List<Pair<IrKey, String>> = listOf(
    AmpKeys.power to "Amp power (toggle)",
    AmpKeys.inputNext to "Amp next input",
    AmpKeys.inputPrevious to "Amp previous input",
    AmpKeys.mute to "Amp mute",
    TvIrKeys.power to "TV power over IR (toggle)",
    TvIrKeys.source to "TV source",
    TvIrKeys.mute to "TV mute",
) + HdmiKeys.inputs.map { it to it.label } + listOf(HdmiKeys.next to "HDMI next input")

/** The bridge key of an IR step (a key this app does not list still gets sent: the bridge may know it). */
fun irKeyOf(step: Step): IrKey = ACTION_IR_KEYS.firstOrNull { it.first.bridgeName == step.key }?.first ?: IrKey(step.key, step.key)

/** What a step does, in a few words; [outputName] names a switch output by its key (null: no longer there). */
fun stepTitle(step: Step, outputName: (String) -> String?): String = when (step.kind) {
    StepKind.STICK_ON -> "Android TV on"
    StepKind.STICK_OFF -> "Android TV off"
    StepKind.TV_WAKE -> "TV on (Wake-on-LAN)"
    StepKind.IR -> ACTION_IR_KEYS.firstOrNull { it.first.bridgeName == step.key }?.second ?: step.key
    StepKind.SWITCH_ON -> (outputName(step.output) ?: "Removed switch") + " on"
    StepKind.SWITCH_OFF -> (outputName(step.output) ?: "Removed switch") + " off"
    StepKind.WAIT -> "Wait ${step.seconds} s"
}

/** The stick and the TV's network side, for running steps; implemented by the Android TV view model. */
interface ActionTargets {
    /** Brings the stick to [on]; null when done, else why not. */
    suspend fun stickPower(on: Boolean): String?

    /** Sends Wake-on-LAN to the TV; false when no TV with a MAC address is set up. */
    fun wakeTv(): Boolean
}
