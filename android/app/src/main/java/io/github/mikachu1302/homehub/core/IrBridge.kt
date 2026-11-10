package io.github.mikachu1302.homehub.core

/**
 * A key on the IR bridge. [bridgeName] must match a button `name` in esphome/atom-lite-ir.yaml exactly:
 * the bridge addresses buttons by name (docs/PROTOCOLS.md).
 */
data class IrKey(val label: String, val bridgeName: String)

/**
 * Placeholder until the bridge is set up (docs/PLAN.md, phase 1): the amp keys the config already defines.
 * Inputs and further keys are added once the real remote has been captured.
 */
object AmpKeys {
    val power = IrKey("Power", "Amp Power")
    val mute = IrKey("Mute", "Amp Mute")
    val volumeUp = IrKey("Volume up", "Amp Volume Up")
    val volumeDown = IrKey("Volume down", "Amp Volume Down")
    val inputs: List<IrKey> = emptyList()
}

/** `POST` path that presses [key] on the bridge; the name is percent-encoded (spaces as %20). */
fun irPressPath(key: IrKey): String =
    "/button/" + java.net.URLEncoder.encode(key.bridgeName, "UTF-8").replace("+", "%20") + "/press"
