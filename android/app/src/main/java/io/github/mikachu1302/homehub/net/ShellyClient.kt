package io.github.mikachu1302.homehub.net

import io.github.mikachu1302.homehub.core.ShellyInfo
import io.github.mikachu1302.homehub.core.ShellyJson
import io.github.mikachu1302.homehub.core.SwitchConfig
import io.github.mikachu1302.homehub.core.SwitchStatus
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

/** Talks to Shelly Gen2/Gen3 devices with JSON-RPC over `POST /rpc` (docs/PROTOCOLS.md). */
class ShellyClient(private val http: LanHttp = LanHttp()) {
    private val ids = AtomicInteger(1)

    private suspend fun rpc(address: String, method: String, params: JSONObject? = null): JSONObject =
        ShellyJson.result(http.post(address, "/rpc", ShellyJson.request(ids.getAndIncrement(), method, params)))

    /** `GET /shelly`: null when the host is not a Gen2+ Shelly. */
    suspend fun identify(address: String): ShellyInfo? = ShellyJson.parseIdentity(http.get(address, "/shelly"))

    suspend fun switches(address: String): List<SwitchStatus> = ShellyJson.parseSwitches(rpc(address, "Shelly.GetStatus"))

    /** Switches one channel. The answer is the state *before* the change, so callers read the status back. */
    suspend fun set(address: String, channel: Int, on: Boolean) {
        rpc(address, "Switch.Set", ShellyJson.setParams(channel, on))
    }

    suspend fun config(address: String, channel: Int): SwitchConfig =
        ShellyJson.parseSwitchConfig(rpc(address, "Switch.GetConfig", JSONObject().put("id", channel)))

    suspend fun setConfig(address: String, config: SwitchConfig) {
        rpc(address, "Switch.SetConfig", ShellyJson.setConfigParams(config))
    }
}
