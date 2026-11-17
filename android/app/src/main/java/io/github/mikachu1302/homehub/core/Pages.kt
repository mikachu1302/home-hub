package io.github.mikachu1302.homehub.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * The ready-made blocks a custom page is built from (docs/DESIGN.md, "Custom pages"). [id] is stored and shared, so it
 * never changes; a block an older app does not know is dropped on reading.
 */
enum class BlockKind(val id: String, val title: String, val needsOutput: Boolean = false) {
    AMP_VOLUME("amp_volume", "Amplifier · volume and mute"),
    AMP_KEYS("amp_keys", "Amplifier · power and input"),
    TV_IR_PAD("tv_ir_pad", "TV · arrows, OK, back"),
    TV_IR_KEYS("tv_ir_keys", "TV · power, source, mute"),
    TV_IR_VOLUME("tv_ir_volume", "TV · volume"),
    HDMI("hdmi", "HDMI Switch · inputs 1–5, next"),
    SWITCH("switch", "Switch · one output", needsOutput = true),
    ATV_APPS("atv_apps", "Android TV · app shortcuts"),
    ATV_PAD("atv_pad", "Android TV · arrows and OK"),
    ATV_KEYS("atv_keys", "Android TV · power, home, back, menu, play"),
    ATV_VOLUME("atv_volume", "Android TV · volume and mute"),
    ATV_NUMBERS("atv_numbers", "Android TV · number pad"),
    ATV_MORE("atv_more", "Android TV · playback and more keys"),
    ;

    /** A block that talks to the Android TV stick (tv-remote's protocol), not to the IR bridge. */
    val androidTv: Boolean get() = id.startsWith("atv_")

    companion object {
        fun byId(id: String): BlockKind? = entries.firstOrNull { it.id == id }
    }
}

/** One block on a page; [output] is the output key (`<device id>/<channel>`) of a [BlockKind.SWITCH] block. */
data class Block(val kind: BlockKind, val output: String = "")

/** One swipeable part of a page: blocks from top to bottom. */
data class PageSection(val blocks: List<Block> = emptyList())

/**
 * A page the user built: a title and one or more sections. With more than one, the page swipes sideways between them
 * (tv-remote's pager). [id] is made once and never changes.
 */
data class CustomPage(val id: String, val title: String, val sections: List<PageSection> = listOf(PageSection())) {
    /** Every block on the page, section after section. */
    val blocks: List<Block> get() = sections.flatMap { it.blocks }
}

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
                    "sections",
                    JSONArray().apply {
                        p.sections.forEach { s ->
                            put(JSONObject().put("blocks", JSONArray().apply { s.blocks.forEach { put(blockJson(it)) } }))
                        }
                    },
                ),
            )
        }
    }

    private fun blockJson(b: Block) = JSONObject().put("kind", b.kind.id).apply { if (b.kind.needsOutput) put("output", b.output) }

    private fun blocks(b: JSONArray?): List<Block> = if (b == null) emptyList() else (0 until b.length()).mapNotNull { j ->
        val bo = b.optJSONObject(j) ?: return@mapNotNull null
        val kind = BlockKind.byId(bo.optString("kind")) ?: return@mapNotNull null
        Block(kind, if (kind.needsOutput) bo.optString("output") else "")
    }

    /**
     * Pages without an id are dropped, a missing title becomes "Page", unknown blocks are dropped. A page from before
     * sections (a plain "blocks" list) becomes one section; a page always has at least one.
     */
    fun fromJson(a: JSONArray?): List<CustomPage> {
        if (a == null) return emptyList()
        return (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id").trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val sections = o.optJSONArray("sections")?.let { a ->
                (0 until a.length()).mapNotNull { j -> a.optJSONObject(j)?.let { PageSection(blocks(it.optJSONArray("blocks"))) } }
            } ?: listOf(PageSection(blocks(o.optJSONArray("blocks"))))
            CustomPage(id, o.optString("title").trim().ifEmpty { "Page" }, sections.ifEmpty { listOf(PageSection()) })
        }.distinctBy { it.id }
    }

    fun encode(pages: List<CustomPage>): String = toJson(pages).toString()

    fun decode(text: String?): List<CustomPage> =
        runCatching { fromJson(JSONArray(text ?: "[]")) }.getOrDefault(emptyList())
}
