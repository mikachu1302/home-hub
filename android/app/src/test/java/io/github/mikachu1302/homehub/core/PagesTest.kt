package io.github.mikachu1302.homehub.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Custom pages: storage round trip and ids. Reading and the menu order run from spec/fixtures (FixtureTest). Made-up output keys. */
class PagesTest {
    private fun page(id: String, title: String, vararg sections: List<Block>) = CustomPage(id, title, sections.map { PageSection(it) })

    private val movie = page(
        "k3", "Movie night",
        listOf(Block(BlockKind.HDMI), Block(BlockKind.SWITCH, "shellyplus1pm-112233445566/0")),
        listOf(Block(BlockKind.AMP_VOLUME), Block(BlockKind.HDMI)),
    )
    private val radio = page("z9", "Radio", listOf(Block(BlockKind.AMP_VOLUME)))

    @Test
    fun jsonRoundTrip() {
        val pages = listOf(movie, radio, CustomPage("e1", "Empty"))
        assertEquals(pages, PagesJson.decode(PagesJson.encode(pages)))
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
            listOf(
                "amp_volume", "amp_keys", "tv_ir_pad", "tv_ir_keys", "tv_ir_volume", "hdmi", "switch",
                "atv_apps", "atv_pad", "atv_keys", "atv_volume", "atv_numbers", "atv_more", "action",
            ),
            BlockKind.entries.map { it.id },
        )
    }

    @Test
    fun stepIdsNeverChange() {
        // Stored and shared inside action blocks.
        assertEquals(
            listOf("stick_on", "stick_off", "tv_wake", "ir", "switch_on", "switch_off", "wait"),
            StepKind.entries.map { it.id },
        )
    }

    @Test
    fun actionsRoundTripAndGetFreshIds() {
        val action = Action(
            "a1", "Movie",
            listOf(Step(StepKind.STICK_ON), Step(StepKind.IR, key = "Amp Power"), Step(StepKind.WAIT, seconds = 3), Step(StepKind.SWITCH_OFF, output = "shellyplus1pm-112233445566/0")),
        )
        val pages = listOf(CustomPage("k3", "Movie night", listOf(PageSection(listOf(Block(BlockKind.ACTION, action = action), Block(BlockKind.HDMI))))))
        assertEquals(pages, PagesJson.decode(PagesJson.encode(pages)))
        // An action on some page already has id "0", so the first random value is skipped.
        val values = ArrayDeque(listOf(0L, 1L))
        assertEquals("1", newActionId(listOf(CustomPage("x", "x", listOf(PageSection(listOf(Block(BlockKind.ACTION, action = Action("0", "x")))))))) { values.removeFirst() })
    }

    @Test
    fun stepTitlesNameTheOutputOrSayItIsGone() {
        assertEquals("Amp power (toggle)", stepTitle(Step(StepKind.IR, key = "Amp Power")) { null })
        assertEquals("Ceiling on", stepTitle(Step(StepKind.SWITCH_ON, output = "x/0")) { "Ceiling" })
        assertEquals("Removed switch off", stepTitle(Step(StepKind.SWITCH_OFF, output = "x/0")) { null })
        assertEquals("Some New Key", stepTitle(Step(StepKind.IR, key = "Some New Key")) { null })
    }
}
