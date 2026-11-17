package io.github.mikachu1302.homehub.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * The ready-made blocks a custom page is built from (docs/DESIGN.md, "Custom pages"). [id] is stored and shared, so it
 * never changes; a block an older app does not know is dropped on reading. tv-remote's blocks (apps, number pad, TV
 * volume) come with the TV remote.
 */
enum class BlockKind(val id: String, val title: String, val needsOutput: Boolean = false) {
    AMP_VOLUME("amp_volume", "Amplifier · volume and mute"),
    AMP_KEYS("amp_keys", "Amplifier · power and input"),
    TV_IR_PAD("tv_ir_pad", "TV (IR) · arrows, OK, back"),
    TV_IR_KEYS("tv_ir_keys", "TV (IR) · power, source, mute"),
    HDMI("hdmi", "HDMI switch · inputs 1–5, next"),
    SWITCH("switch", "Switch · one output", needsOutput = true),
    ;

    companion object {
        fun byId(id: String): BlockKind? = entries.firstOrNull { it.id == id }
    }
}

/** One block on a page; [output] is the output key (`<device id>/<channel>`) of a [BlockKind.SWITCH] block. */
data class Block(val kind: BlockKind, val output: String = "")

/** A page the user built: a title and blocks from top to bottom. [id] is made once and never changes. */
data class CustomPage(val id: String, val title: String, val blocks: List<Block> = emptyList())

/** Menu ids: a built-in section is its `Section` name, a custom page `page:<id>`. */
const val PAGE_PREFIX = "page:"

fun pageMenuId(page: CustomPage) = PAGE_PREFIX + page.id

/** All menu ids (built-in sections, then the custom pages) in the user's order; unknown ids are ignored, new ones come last. */
fun menuIds(order: List<String>, pages: List<CustomPage>): List<String> =
    sortByOrder(MENU_SECTIONS.map { it.name } + pages.map(::pageMenuId), { it }, order)

/** An id for a new page that none of [pages] uses. */
fun newPageId(pages: List<CustomPage>, random: () -> Long): String {
    val used = pages.map { it.id }.toSet()
    while (true) {
        val id = java.lang.Long.toString(random() and 0xFFFFFFFFL, 36)
        if (id !in used) return id
    }
}

object PagesJson {
    fun toJson(pages: List<CustomPage>): JSONArray = JSONArray().apply {
        pages.forEach { p ->
            put(
                JSONObject().put("id", p.id).put("title", p.title).put(
                    "blocks",
                    JSONArray().apply {
                        p.blocks.forEach { b ->
                            put(JSONObject().put("kind", b.kind.id).apply { if (b.kind.needsOutput) put("output", b.output) })
                        }
                    },
                ),
            )
        }
    }

    /** Pages without an id are dropped, a missing title becomes "Page", unknown blocks are dropped. */
    fun fromJson(a: JSONArray?): List<CustomPage> {
        if (a == null) return emptyList()
        return (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id").trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val blocks = o.optJSONArray("blocks")?.let { b ->
                (0 until b.length()).mapNotNull { j ->
                    val bo = b.optJSONObject(j) ?: return@mapNotNull null
                    val kind = BlockKind.byId(bo.optString("kind")) ?: return@mapNotNull null
                    Block(kind, if (kind.needsOutput) bo.optString("output") else "")
                }
            } ?: emptyList()
            CustomPage(id, o.optString("title").trim().ifEmpty { "Page" }, blocks)
        }.distinctBy { it.id }
    }

    fun encode(pages: List<CustomPage>): String = toJson(pages).toString()

    fun decode(text: String?): List<CustomPage> =
        runCatching { fromJson(JSONArray(text ?: "[]")) }.getOrDefault(emptyList())
}
