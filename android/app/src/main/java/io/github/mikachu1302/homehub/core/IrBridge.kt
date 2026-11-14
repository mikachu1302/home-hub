package io.github.mikachu1302.homehub.core

/**
 * A key on the IR bridge. [bridgeName] must match a button `name` in esphome/atom-lite-ir.yaml exactly:
 * the bridge addresses buttons by name (docs/PROTOCOLS.md).
 */
data class IrKey(val label: String, val bridgeName: String)

/** The bridge's web login (a credential: Keystore only, shared only behind "Include passwords"). */
data class BridgeLogin(val user: String, val password: String) {
    override fun toString() = "BridgeLogin($user, ***)"
}

/** The amp keys the bridge defines (esphome/atom-lite-ir.yaml); all confirmed on the A-9050. */
object AmpKeys {
    val power = IrKey("Power", "Amp Power")
    val mute = IrKey("Mute", "Amp Mute")
    val volumeUp = IrKey("Volume up", "Amp Volume Up")
    val volumeDown = IrKey("Volume down", "Amp Volume Down")
    /** The remote has no key per input, only next/previous (the amp steps through its inputs). */
    val inputNext = IrKey("Next input", "Amp Input Next")
    val inputPrevious = IrKey("Previous input", "Amp Input Previous")
}

/** `POST` path that presses [key] on the bridge; the name is percent-encoded (spaces as %20). */
fun irPressPath(key: IrKey): String = irButtonPath(key) + "/press"

/** `GET` path of [key]'s state: proves the button exists (and the login works) without sending anything. */
fun irButtonPath(key: IrKey): String =
    "/button/" + java.net.URLEncoder.encode(key.bridgeName, "UTF-8").replace("+", "%20")
