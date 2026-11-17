package io.github.mikachu1302.homehub.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Custom pages: menu order, storage JSON, ids. Made-up output keys. */
class PagesTest {
    private fun page(id: String, title: String, vararg sections: List<Block>) = CustomPage(id, title, sections.map { PageSection(it) })

    private val movie = page(
        "k3", "Movie night",
        listOf(Block(BlockKind.HDMI), Block(BlockKind.SWITCH, "shellyplus1pm-112233445566/0")),
        listOf(Block(BlockKind.AMP_VOLUME), Block(BlockKind.HDMI)),
    )
    private val radio = page("z9", "Radio", listOf(Block(BlockKind.AMP_VOLUME)))

    @Test
    fun menuListsSectionsThenPagesInTheUsersOrder() {
        assertEquals(MENU_SECTIONS.map { it.name } + listOf("page:k3", "page:z9"), menuIds(emptyList(), listOf(movie, radio)))
        assertEquals(
            listOf("page:z9", "HDMI", "SWITCHES", "AMP", "TV", "TV_IR", "page:k3"),
            menuIds(listOf("page:z9", "page:gone", "HDMI", "SWITCHES"), listOf(movie, radio)),
        )
    }

    @Test
    fun jsonRoundTrip() {
        val pages = listOf(movie, radio, CustomPage("e1", "Empty"))
        assertEquals(pages, PagesJson.decode(PagesJson.encode(pages)))
    }

    @Test
    fun readingIsForgiving() {
        val text = """[{"id":"a","blocks":[{"kind":"nope"},{"kind":"hdmi","output":"ignored"}]},{"title":"x"},{"id":"a","title":"dup"}]"""
        assertEquals(listOf(page("a", "Page", listOf(Block(BlockKind.HDMI)))), PagesJson.decode(text))
        assertEquals(emptyList<CustomPage>(), PagesJson.decode("not json"))
        assertEquals(emptyList<CustomPage>(), PagesJson.decode(null))
    }

    @Test
    fun aPageFromBeforeSectionsIsOneSection() {
        val old = """[{"id":"k3","title":"Old","blocks":[{"kind":"hdmi"},{"kind":"amp_keys"}]}]"""
        assertEquals(listOf(page("k3", "Old", listOf(Block(BlockKind.HDMI), Block(BlockKind.AMP_KEYS)))), PagesJson.decode(old))
        // No sections at all: one empty section, so the editor always has one to fill.
        assertEquals(listOf(CustomPage("e", "Empty")), PagesJson.decode("""[{"id":"e","title":"Empty","sections":[]}]"""))
    }

    @Test
    fun blocksListsEverySection() {
        assertEquals(4, movie.blocks.size)
    }

    @Test
    fun newIdsAreUnused() {
        // The first random value gives an id that is taken ("0"), the next one is free.
        val values = ArrayDeque(listOf(0L, 35L))
        assertEquals("z", newPageId(listOf(CustomPage("0", "x"))) { values.removeFirst() })
        assertNotEquals("k3", newPageId(listOf(movie)) { 1L })
    }

    @Test
    fun blockIdsNeverChange() {
        // Stored and shared: renaming one would drop it from every saved page.
        assertEquals(
            listOf("amp_volume", "amp_keys", "tv_ir_pad", "tv_ir_keys", "tv_ir_volume", "hdmi", "switch"),
            BlockKind.entries.map { it.id },
        )
    }
}
