package com.crosspaste.cli.commands.pick

import com.crosspaste.cli.commands.PasteSummaryDto
import com.github.ajalt.mordant.input.KeyboardEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun item(
    id: Long,
    preview: String,
    typeName: String = "text",
) = PasteSummaryDto(
    id = id,
    typeName = typeName,
    source = null,
    size = preview.length.toLong(),
    tagged = false,
    createTime = id,
    preview = preview,
    remote = false,
)

private fun state(
    vararg items: PasteSummaryDto,
    tags: List<String> = emptyList(),
): PickState =
    PickState(query = "", filters = PickFilters(types = emptyList(), tag = null), tagNames = tags).apply {
        setWindow(items.toList(), items.size.toLong())
    }

class PickModelTest {

    @Test
    fun typingFiltersRowsAndAsksForADebouncedRefetch() {
        val s = state(item(1, "docker compose"), item(2, "hello world"))
        val effect = s.handle(PickAction.Insert("dock"), pageSize = 10)
        assertIs<PickEffect.RefetchDebounced>(effect)
        assertEquals(listOf(1L), s.rows.map { it.item.id })
    }

    @Test
    fun queryRanksByScoreWhileEmptyQueryKeepsServerOrder() {
        val s = state(item(1, "a docker at the end docker"), item(2, "docker first"))
        s.handle(PickAction.Insert("docker"), pageSize = 10)
        assertEquals(listOf(2L, 1L), s.rows.map { it.item.id })
        s.handle(PickAction.ClearOrCancel, pageSize = 10)
        assertEquals(listOf(1L, 2L), s.rows.map { it.item.id })
    }

    @Test
    fun selectionClampsAtTheEdges() {
        val s = state(item(1, "one"), item(2, "two"))
        assertIs<PickEffect.None>(s.handle(PickAction.MoveUp, pageSize = 10))
        assertIs<PickEffect.Redraw>(s.handle(PickAction.MoveDown, pageSize = 10))
        assertIs<PickEffect.None>(s.handle(PickAction.MoveDown, pageSize = 10))
        assertEquals(1, s.selected)
        assertIs<PickEffect.Redraw>(s.handle(PickAction.Home, pageSize = 10))
        assertEquals(0, s.selected)
    }

    @Test
    fun escClearsHelpThenQueryThenCancels() {
        val s = state(item(1, "docker"))
        s.handle(PickAction.ToggleHelp, pageSize = 10)
        assertIs<PickEffect.Redraw>(s.handle(PickAction.ClearOrCancel, pageSize = 10))
        s.handle(PickAction.Insert("d"), pageSize = 10)
        assertIs<PickEffect.RefetchDebounced>(s.handle(PickAction.ClearOrCancel, pageSize = 10))
        assertEquals("", s.query)
        assertIs<PickEffect.Cancel>(s.handle(PickAction.ClearOrCancel, pageSize = 10))
    }

    @Test
    fun acceptReturnsTheSelectedItemAndEditGatesOnEditableTypes() {
        val s = state(item(1, "screenshot", typeName = "image"), item(2, "notes"))
        assertIs<PickEffect.None>(s.handle(PickAction.EditSelected, pageSize = 10))
        s.handle(PickAction.MoveDown, pageSize = 10)
        val edit = s.handle(PickAction.EditSelected, pageSize = 10)
        assertEquals(2L, (edit as PickEffect.Edit).item.id)
        val accept = s.handle(PickAction.Accept, pageSize = 10)
        assertEquals(2L, (accept as PickEffect.Accept).item.id)
    }

    @Test
    fun filterBarOpensAndArrowsApplyValuesLive() {
        val s = state(item(1, "one"), tags = listOf("Work", "Home"))
        // Arrows without a bar do nothing to filters
        assertIs<PickEffect.None>(s.handle(PickAction.MoveRight, pageSize = 10))
        assertIs<PickEffect.Redraw>(s.handle(PickAction.ToggleTypeBar, pageSize = 10))
        assertEquals(FilterBarKind.TYPE, s.filterBar)
        assertIs<PickEffect.RefetchNow>(s.handle(PickAction.MoveRight, pageSize = 10))
        assertEquals(listOf("text"), s.filters.types)
        assertIs<PickEffect.RefetchNow>(s.handle(PickAction.MoveLeft, pageSize = 10))
        assertEquals(emptyList(), s.filters.types)
        // Switching to the tag bar replaces the type bar
        s.handle(PickAction.ToggleTagBar, pageSize = 10)
        assertEquals(FilterBarKind.TAG, s.filterBar)
        assertIs<PickEffect.RefetchNow>(s.handle(PickAction.MoveRight, pageSize = 10))
        assertEquals("Work", s.filters.tag)
        // Enter confirms the bar without copying; the sort toggle stays direct
        assertIs<PickEffect.Redraw>(s.handle(PickAction.Accept, pageSize = 10))
        assertEquals(null, s.filterBar)
        assertIs<PickEffect.RefetchNow>(s.handle(PickAction.ToggleSort, pageSize = 10))
        assertEquals(false, s.filters.sortNewest)
    }

    @Test
    fun escAndTypingCloseTheFilterBar() {
        val s = state(item(1, "one"), tags = listOf("Work"))
        s.handle(PickAction.ToggleTagBar, pageSize = 10)
        assertIs<PickEffect.Redraw>(s.handle(PickAction.ClearOrCancel, pageSize = 10))
        assertEquals(null, s.filterBar)
        s.handle(PickAction.ToggleTagBar, pageSize = 10)
        s.handle(PickAction.Insert("a"), pageSize = 10)
        assertEquals(null, s.filterBar)
        assertEquals("a", s.query)
    }

    @Test
    fun searchExtraMergesById() {
        val s = state(item(1, "docker compose"))
        s.setSearchExtra(listOf(item(1, "docker compose"), item(9, "docker run")))
        assertEquals(listOf(1L, 9L), s.rows.map { it.item.id })
    }

    @Test
    fun serverResultsSurviveTheLocalFuzzyFilter() {
        // The server also matches source and normalizes punctuation, so its
        // hits stay visible even when the preview alone cannot re-derive them
        val s = state(item(1, "some page title"))
        s.handle(PickAction.Insert("chrome"), pageSize = 10)
        assertTrue(s.rows.isEmpty())
        s.setSearchExtra(listOf(item(9, "matched via source, not preview")))
        assertEquals(listOf(9L), s.rows.map { it.item.id })
        // ...but they rank below rows the local matcher actually scored
        s.setWindow(listOf(item(1, "chrome settings"), item(2, "unrelated")), 2)
        assertEquals(listOf(1L, 9L), s.rows.map { it.item.id })
    }

    @Test
    fun editingTheQueryDropsStaleServerResults() {
        val s = state(item(1, "local row"))
        s.handle(PickAction.Insert("q"), pageSize = 10)
        s.setSearchExtra(listOf(item(9, "server hit for old query")))
        assertEquals(listOf(9L), s.rows.map { it.item.id })
        s.handle(PickAction.Insert("x"), pageSize = 10)
        assertTrue(s.rows.isEmpty())
    }

    @Test
    fun clearForRefetchDropsEverythingUntilTheNewWindowArrives() {
        val s = state(item(1, "one"), item(2, "two"))
        s.setSearchExtra(listOf(item(9, "extra")))
        s.clearForRefetch()
        assertTrue(s.rows.isEmpty())
        assertIs<PickEffect.None>(s.handle(PickAction.Accept, pageSize = 10))
    }

    @Test
    fun tagShiftWrapsThroughNoneInBothDirections() {
        assertEquals("Work", shiftTag(null, listOf("Work", "Home"), 1))
        assertEquals("Home", shiftTag("Work", listOf("Work", "Home"), 1))
        assertNull(shiftTag("Home", listOf("Work", "Home"), 1))
        assertEquals("Home", shiftTag(null, listOf("Work", "Home"), -1))
    }

    @Test
    fun typeShiftWrapsAndTreatsAnInitialMultiTypeFilterAsForeign() {
        assertEquals(listOf("text"), shiftTypeStage(emptyList(), 1))
        assertEquals(listOf("color"), shiftTypeStage(emptyList(), -1))
        assertEquals(emptyList(), shiftTypeStage(listOf("color"), 1))
        assertEquals(emptyList(), shiftTypeStage(listOf("text", "link"), 1))
    }

    @Test
    fun dropLastCodepointKeepsSurrogatePairsIntact() {
        assertEquals("ab", dropLastCodepoint("abc"))
        assertEquals("a", dropLastCodepoint("a🎉"))
        assertEquals("", dropLastCodepoint(""))
    }

    @Test
    fun keyMappingCoversControlAndNamedKeys() {
        assertEquals(PickAction.MoveUp, toPickAction(KeyboardEvent("p", ctrl = true), queryEmpty = true))
        assertEquals(PickAction.EditSelected, toPickAction(KeyboardEvent("e", ctrl = true), queryEmpty = true))
        assertEquals(PickAction.Cancel, toPickAction(KeyboardEvent("c", ctrl = true), queryEmpty = true))
        assertEquals(PickAction.MoveUp, toPickAction(KeyboardEvent("ArrowUp"), queryEmpty = true))
        assertEquals(PickAction.Accept, toPickAction(KeyboardEvent("Enter"), queryEmpty = true))
        assertEquals(PickAction.TogglePreview, toPickAction(KeyboardEvent("Tab"), queryEmpty = true))
        assertEquals(PickAction.ClearOrCancel, toPickAction(KeyboardEvent("Escape"), queryEmpty = true))
        assertEquals(PickAction.ToggleHelp, toPickAction(KeyboardEvent("?"), queryEmpty = true))
        assertEquals(PickAction.Insert("?"), toPickAction(KeyboardEvent("?"), queryEmpty = false))
        assertEquals(PickAction.Insert("a"), toPickAction(KeyboardEvent("a"), queryEmpty = true))
        assertEquals(PickAction.Insert("剪"), toPickAction(KeyboardEvent("剪"), queryEmpty = true))
        assertNull(toPickAction(KeyboardEvent("F1"), queryEmpty = true))
        assertNull(toPickAction(KeyboardEvent("x", alt = true), queryEmpty = true))
    }

    @Test
    fun selectionFollowsThePasteIdWhenRowsShift() {
        val s = state(item(3, "three"), item(2, "two"), item(1, "one"))
        s.handle(PickAction.MoveDown, pageSize = 10)
        assertEquals(2L, s.selectedItem?.id)
        // A new paste lands on top (e.g. after Ctrl-E): the selected ID keeps
        // pointing at the same paste even though its index moved
        s.setWindow(listOf(item(9, "edited"), item(3, "three"), item(2, "two"), item(1, "one")), 4)
        assertEquals(2L, s.selectedItem?.id)
        // Re-ranking via merged search results also preserves it
        s.setSearchExtra(listOf(item(8, "extra")))
        assertEquals(2L, s.selectedItem?.id)
        // Only when the paste vanishes does the index fallback apply
        s.setWindow(listOf(item(9, "edited"), item(1, "one")), 2)
        assertEquals(2, s.selected.coerceAtLeast(0).let { it })
        assertTrue(s.selectedItem != null)
    }

    @Test
    fun emptyResultKeepsSelectionUsable() {
        val s = state(item(1, "one"))
        s.handle(PickAction.Insert("zzz"), pageSize = 10)
        assertTrue(s.rows.isEmpty())
        assertIs<PickEffect.None>(s.handle(PickAction.Accept, pageSize = 10))
        assertEquals(0, s.selected)
    }
}
