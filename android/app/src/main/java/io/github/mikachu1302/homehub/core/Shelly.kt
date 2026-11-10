package io.github.mikachu1302.homehub.core

import org.json.JSONException
import org.json.JSONObject

/** A Gen2+ Shelly as it answers `GET /shelly`. */
data class ShellyInfo(val id: String, val model: String, val gen: Int, val app: String, val auth: Boolean)

/** One relay channel's live state (`switch:N` in `Shelly.GetStatus`). Power and voltage are optional. */
data class SwitchStatus(val channel: Int, val on: Boolean, val watts: Double?, val voltage: Double?)

/** One relay channel's timers (`Switch.GetConfig`), delays in seconds. They run on the device itself. */
data class SwitchConfig(
    val channel: Int,
    val autoOff: Boolean,
    val autoOffDelay: Double,
    val autoOn: Boolean,
    val autoOnDelay: Double,
)

/** The device answered, but with an error or something we cannot read. */
class ShellyException(message: String) : Exception(message)

/**
 * Parsing and request building for the Shelly Gen2/Gen3 JSON-RPC API (docs/PROTOCOLS.md).
 * Pure: no I/O. The cases in spec/fixtures/shelly-*.json are the contract.
 */
object ShellyJson {

    /** `GET /shelly` → identity, or null when the answer is not from a Gen2+ Shelly. */
    fun parseIdentity(text: String): ShellyInfo? {
        val o = try {
            JSONObject(text)
        } catch (_: JSONException) {
            return null
        }
        val id = o.optString("id").takeIf { it.isNotEmpty() } ?: return null
        val gen = o.optInt("gen", 0)
        if (gen < 2) return null
        return ShellyInfo(
            id = id,
            model = o.optString("model"),
            gen = gen,
            app = o.optString("app"),
            auth = o.optBoolean("auth_en", false),
        )
    }

    /** A JSON-RPC request body for `POST /rpc`. */
    fun request(id: Int, method: String, params: JSONObject? = null): String {
        val o = JSONObject().put("id", id).put("method", method)
        if (params != null) o.put("params", params)
        return o.toString()
    }

    /** The `result` of a JSON-RPC answer; throws [ShellyException] with the device's message on an error. */
    fun result(text: String): JSONObject {
        val o = try {
            JSONObject(text)
        } catch (_: JSONException) {
            throw ShellyException("Unexpected answer")
        }
        o.optJSONObject("result")?.let { return it }
        val error = o.optJSONObject("error")
        if (error != null) throw ShellyException(error.optString("message").ifEmpty { "Error ${error.optInt("code")}" })
        throw ShellyException("Unexpected answer")
    }

    /** `Shelly.GetStatus` result → every `switch:N`, sorted by channel. */
    fun parseSwitches(result: JSONObject): List<SwitchStatus> =
        result.keys().asSequence()
            .filter { it.startsWith("switch:") }
            .mapNotNull { key ->
                val s = result.optJSONObject(key) ?: return@mapNotNull null
                val channel = key.removePrefix("switch:").toIntOrNull() ?: return@mapNotNull null
                if (!s.has("output")) return@mapNotNull null
                SwitchStatus(
                    channel = channel,
                    on = s.optBoolean("output"),
                    watts = s.optDoubleOrNull("apower"),
                    voltage = s.optDoubleOrNull("voltage"),
                )
            }
            .sortedBy { it.channel }
            .toList()

    /** `Switch.GetConfig` result → timers. */
    fun parseSwitchConfig(result: JSONObject): SwitchConfig = SwitchConfig(
        channel = result.optInt("id"),
        autoOff = result.optBoolean("auto_off"),
        autoOffDelay = result.optDouble("auto_off_delay", 60.0),
        autoOn = result.optBoolean("auto_on"),
        autoOnDelay = result.optDouble("auto_on_delay", 60.0),
    )

    /** Params for `Switch.SetConfig`: only the timer fields, everything else on the device stays as it is. */
    fun setConfigParams(c: SwitchConfig): JSONObject = JSONObject()
        .put("id", c.channel)
        .put(
            "config",
            JSONObject()
                .put("auto_off", c.autoOff)
                .put("auto_off_delay", c.autoOffDelay)
                .put("auto_on", c.autoOn)
                .put("auto_on_delay", c.autoOnDelay),
        )

    fun setParams(channel: Int, on: Boolean): JSONObject = JSONObject().put("id", channel).put("on", on)

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (has(key) && !isNull(key)) optDouble(key).takeUnless { it.isNaN() } else null
}
