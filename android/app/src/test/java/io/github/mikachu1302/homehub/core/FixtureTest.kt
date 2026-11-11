package io.github.mikachu1302.homehub.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.net.InetAddress

/** Runs every case in spec/fixtures (made-up data) against the core parsers. */
class FixtureTest {

    private fun cases(file: String): List<JSONObject> {
        val dir = System.getProperty("fixtures") ?: error("fixtures system property not set")
        val arr = JSONObject(File(dir, file).readText()).getJSONArray("cases")
        assertTrue("$file has no cases", arr.length() > 0)
        return (0 until arr.length()).map { arr.getJSONObject(it) }
    }

    /** A string input is the raw response text; anything else is a JSON document. */
    private fun raw(case: JSONObject): String = when (val v = case.get("input")) {
        is String -> v
        else -> v.toString()
    }

    @Test
    fun identity() = cases("shelly-identity.json").forEach { c ->
        val name = c.getString("name")
        val got = ShellyJson.parseIdentity(raw(c))
        if (c.isNull("expected")) {
            assertNull(name, got)
        } else {
            val e = c.getJSONObject("expected")
            assertEquals(name, ShellyInfo(e.getString("id"), e.getString("model"), e.getInt("gen"), e.getString("app"), e.getBoolean("auth")), got)
        }
    }

    @Test
    fun rpcEnvelope() = cases("shelly-rpc.json").forEach { c ->
        val name = c.getString("name")
        val e = c.getJSONObject("expected")
        if (e.has("result")) {
            assertEquals(name, e.getJSONObject("result").toString(), ShellyJson.result(raw(c)).toString())
        } else {
            try {
                ShellyJson.result(raw(c))
                fail("$name: expected an error")
            } catch (ex: ShellyException) {
                assertEquals(name, e.getString("error"), ex.message)
            }
        }
    }

    @Test
    fun switchStatus() = cases("shelly-switch-status.json").forEach { c ->
        val e = c.getJSONArray("expected")
        val want = (0 until e.length()).map { i ->
            val o = e.getJSONObject(i)
            SwitchStatus(
                o.getInt("channel"),
                o.getBoolean("on"),
                if (o.isNull("watts")) null else o.getDouble("watts"),
                if (o.isNull("voltage")) null else o.getDouble("voltage"),
            )
        }
        assertEquals(c.getString("name"), want, ShellyJson.parseSwitches(JSONObject(raw(c))))
    }

    @Test
    fun switchConfig() = cases("shelly-switch-config.json").forEach { c ->
        val e = c.getJSONObject("expected")
        val want = SwitchConfig(
            e.getInt("channel"), e.getBoolean("autoOff"), e.getDouble("autoOffDelay"),
            e.getBoolean("autoOn"), e.getDouble("autoOnDelay"),
        )
        assertEquals(c.getString("name"), want, ShellyJson.parseSwitchConfig(JSONObject(raw(c))))
    }

    @Test
    fun delayFormat() = cases("delay-format.json").forEach { c ->
        assertEquals(c.getString("name"), c.getString("expected"), formatDelay(c.getDouble("input")))
    }

    @Test
    fun lanAddress() = cases("lan-address.json").forEach { c ->
        val address = InetAddress.getByName(c.getString("input")) // literals only: no DNS lookup
        assertEquals(c.getString("name"), c.getBoolean("expected"), isLanAddress(address.address))
    }

    @Test
    fun roomGrouping() = cases("room-grouping.json").forEach { c ->
        val input = c.getJSONObject("input")
        val outs = input.getJSONArray("outputs")
        val items = (0 until outs.length()).map { outs.getJSONObject(it).let { o -> o.getString("name") to o.getString("room") } }
        val roomOrder = input.optJSONArray("roomOrder")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
        val e = c.getJSONArray("expected")
        val want = (0 until e.length()).map { i ->
            val g = e.getJSONObject(i)
            val names = g.getJSONArray("items")
            (if (g.isNull("room")) null else g.getString("room")) to (0 until names.length()).map { names.getString(it) }
        }
        val got = groupByRoom(items, { it.second }, roomOrder).map { g -> g.room to g.items.map { it.first } }
        assertEquals(c.getString("name"), want, got)
    }

    @Test
    fun fixturesAreValidJson() {
        val dir = File(System.getProperty("fixtures")!!)
        dir.listFiles { f -> f.extension == "json" }!!.forEach { JSONObject(it.readText()) }
        JSONArray("[]")
    }

    @Test
    fun digestChallenges() {
        val dir = System.getProperty("fixtures") ?: error("fixtures system property not set")
        val all = JSONObject(File(dir, "digest-auth.json").readText())
        val challenges = all.getJSONArray("challenges")
        for (i in 0 until challenges.length()) {
            val c = challenges.getJSONObject(i)
            val name = c.getString("name")
            val got = Digest.parseChallenge(c.getString("input"))
            if (c.isNull("expected")) {
                assertNull(name, got)
            } else {
                val e = c.getJSONObject("expected")
                val want = DigestChallenge(
                    e.getString("realm"),
                    e.getString("nonce"),
                    if (e.isNull("qop")) null else e.getString("qop"),
                    if (e.isNull("opaque")) null else e.getString("opaque"),
                    e.getString("algorithm"),
                )
                assertEquals(name, want, got)
            }
        }
        val responses = all.getJSONArray("responses")
        for (i in 0 until responses.length()) {
            val c = responses.getJSONObject(i)
            fun opt(k: String) = if (c.isNull(k)) null else c.getString(k)
            val ch = DigestChallenge(c.getString("realm"), c.getString("nonce"), opt("qop"), null, c.getString("algorithm"))
            val got = Digest.response(ch, c.getString("user"), c.getString("password"), c.getString("method"), c.getString("uri"), opt("nc"), opt("cnonce"))
            assertEquals(c.getString("name"), c.getString("expected"), got)
        }
    }
}
