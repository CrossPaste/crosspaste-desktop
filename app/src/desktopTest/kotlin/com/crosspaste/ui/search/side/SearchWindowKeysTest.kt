package com.crosspaste.ui.search.side

import androidx.compose.ui.input.key.Key
import com.crosspaste.ui.model.FocusedElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SearchWindowKeysTest {

    private fun preview(
        zone: FocusedElement,
        stroke: SearchKeyStroke,
        isSearchCaretAtEnd: Boolean = true,
    ) = resolveSearchWindowKey(KeyPhase.PREVIEW, zone, stroke, isSearchCaretAtEnd)

    private fun preview(
        zone: FocusedElement,
        key: Key,
    ) = preview(zone, SearchKeyStroke(key))

    private fun bubble(
        key: Key,
        isCtrlPressed: Boolean = false,
        zone: FocusedElement = FocusedElement.PASTE_LIST,
    ) = resolveSearchWindowKey(KeyPhase.BUBBLE, zone, SearchKeyStroke(key, isCtrlPressed = isCtrlPressed))

    @Test
    fun `down in the search input moves to the paste list before the text field sees it`() {
        assertEquals(SearchWindowKeyAction.FOCUS_PASTE_LIST, preview(FocusedElement.SEARCH_INPUT, Key.DirectionDown))
    }

    @Test
    fun `right leaves the search input for the type filter only with the caret at the end`() {
        val right = SearchKeyStroke(Key.DirectionRight)
        assertEquals(SearchWindowKeyAction.FOCUS_TYPE_FILTER, preview(FocusedElement.SEARCH_INPUT, right))
        assertNull(preview(FocusedElement.SEARCH_INPUT, right, isSearchCaretAtEnd = false))
        // Shift+Right extends a selection, it must stay with the text field
        assertNull(preview(FocusedElement.SEARCH_INPUT, right.copy(isShiftPressed = true)))
        assertNull(preview(FocusedElement.SEARCH_INPUT, right.copy(isMetaPressed = true)))
    }

    @Test
    fun `tab walks the top bar left to right, then the list`() {
        val tab = SearchKeyStroke(Key.Tab)
        assertEquals(SearchWindowKeyAction.FOCUS_TYPE_FILTER, preview(FocusedElement.SEARCH_INPUT, tab))
        assertEquals(SearchWindowKeyAction.FOCUS_TAG_BAR, preview(FocusedElement.TYPE_FILTER, tab))
        assertEquals(SearchWindowKeyAction.FOCUS_SETTINGS, preview(FocusedElement.TAG_BAR, tab))
        assertEquals(SearchWindowKeyAction.FOCUS_PASTE_LIST, preview(FocusedElement.SETTINGS, tab))
        assertEquals(SearchWindowKeyAction.FOCUS_SEARCH_INPUT, preview(FocusedElement.PASTE_LIST, tab))
    }

    @Test
    fun `shift tab walks backwards`() {
        val shiftTab = SearchKeyStroke(Key.Tab, isShiftPressed = true)
        assertEquals(SearchWindowKeyAction.FOCUS_PASTE_LIST, preview(FocusedElement.SEARCH_INPUT, shiftTab))
        assertEquals(SearchWindowKeyAction.FOCUS_SEARCH_INPUT, preview(FocusedElement.TYPE_FILTER, shiftTab))
        assertEquals(SearchWindowKeyAction.FOCUS_TYPE_FILTER, preview(FocusedElement.TAG_BAR, shiftTab))
        assertEquals(SearchWindowKeyAction.FOCUS_TAG_BAR, preview(FocusedElement.SETTINGS, shiftTab))
        assertEquals(SearchWindowKeyAction.FOCUS_SETTINGS, preview(FocusedElement.PASTE_LIST, shiftTab))
    }

    @Test
    fun `tab is left alone outside the zones and with other modifiers`() {
        assertNull(preview(FocusedElement.NONE, Key.Tab))
        assertNull(preview(FocusedElement.SEARCH_INPUT, SearchKeyStroke(Key.Tab, isCtrlPressed = true)))
    }

    @Test
    fun `the type filter cycles types with up and down and sits between search and tags`() {
        assertEquals(SearchWindowKeyAction.PREV_PASTE_TYPE, preview(FocusedElement.TYPE_FILTER, Key.DirectionUp))
        assertEquals(SearchWindowKeyAction.NEXT_PASTE_TYPE, preview(FocusedElement.TYPE_FILTER, Key.DirectionDown))
        assertEquals(SearchWindowKeyAction.FOCUS_SEARCH_INPUT, preview(FocusedElement.TYPE_FILTER, Key.DirectionLeft))
        assertEquals(SearchWindowKeyAction.FOCUS_TAG_BAR, preview(FocusedElement.TYPE_FILTER, Key.DirectionRight))
        assertEquals(SearchWindowKeyAction.PASTE, preview(FocusedElement.TYPE_FILTER, Key.Enter))
    }

    @Test
    fun `the type filter swallows typing so the search text field around it never edits the query`() {
        assertEquals(SearchWindowKeyAction.CONSUME, preview(FocusedElement.TYPE_FILTER, Key.A))
        assertEquals(SearchWindowKeyAction.CONSUME, preview(FocusedElement.TYPE_FILTER, Key.Backspace))
    }

    @Test
    fun `the tag bar moves its highlight, toggles and drops to the list`() {
        assertEquals(SearchWindowKeyAction.HIGHLIGHT_PREV_TAG, preview(FocusedElement.TAG_BAR, Key.DirectionLeft))
        assertEquals(SearchWindowKeyAction.HIGHLIGHT_NEXT_TAG, preview(FocusedElement.TAG_BAR, Key.DirectionRight))
        assertEquals(SearchWindowKeyAction.ACTIVATE_TAG, preview(FocusedElement.TAG_BAR, Key.Enter))
        assertEquals(SearchWindowKeyAction.ACTIVATE_TAG, preview(FocusedElement.TAG_BAR, Key.Spacebar))
        assertEquals(SearchWindowKeyAction.FOCUS_PASTE_LIST, preview(FocusedElement.TAG_BAR, Key.DirectionDown))
    }

    @Test
    fun `the settings button opens settings and steps back onto the add chip`() {
        assertEquals(SearchWindowKeyAction.FOCUS_TAG_BAR_END, preview(FocusedElement.SETTINGS, Key.DirectionLeft))
        assertEquals(SearchWindowKeyAction.OPEN_SETTINGS, preview(FocusedElement.SETTINGS, Key.Enter))
        assertEquals(SearchWindowKeyAction.OPEN_SETTINGS, preview(FocusedElement.SETTINGS, Key.Spacebar))
        assertEquals(SearchWindowKeyAction.FOCUS_PASTE_LIST, preview(FocusedElement.SETTINGS, Key.DirectionDown))
        assertNull(preview(FocusedElement.SETTINGS, Key.DirectionRight))
    }

    @Test
    fun `keys the tag bar and settings decline never reach the paste list shortcuts`() {
        for (zone in listOf(FocusedElement.TAG_BAR, FocusedElement.SETTINGS)) {
            assertNull(bubble(Key.Enter, zone = zone))
            assertNull(bubble(Key.DirectionUp, zone = zone))
            assertNull(bubble(Key.DirectionRight, zone = zone))
            assertNull(bubble(Key.N, isCtrlPressed = true, zone = zone))
        }
    }

    @Test
    fun `preview leaves keys alone outside the search input and tag bar`() {
        assertNull(preview(FocusedElement.PASTE_LIST, Key.DirectionDown))
        assertNull(preview(FocusedElement.PASTE_LIST, Key.DirectionRight))
        assertNull(preview(FocusedElement.NONE, Key.DirectionDown))
        assertNull(preview(FocusedElement.SEARCH_INPUT, Key.DirectionLeft))
        assertNull(preview(FocusedElement.SEARCH_INPUT, Key.Enter))
    }

    @Test
    fun `unconsumed keys drive paste, selection and focus`() {
        assertEquals(SearchWindowKeyAction.PASTE, bubble(Key.Enter))
        assertEquals(SearchWindowKeyAction.FOCUS_SEARCH_INPUT, bubble(Key.DirectionUp))
        assertEquals(SearchWindowKeyAction.SELECT_PREV, bubble(Key.DirectionLeft))
        assertEquals(SearchWindowKeyAction.SELECT_NEXT, bubble(Key.DirectionRight))
        assertEquals(SearchWindowKeyAction.PASTE, bubble(Key.Enter, zone = FocusedElement.SEARCH_INPUT))
    }

    @Test
    fun `ctrl n and ctrl p step the selection only with ctrl held`() {
        assertEquals(SearchWindowKeyAction.SELECT_NEXT, bubble(Key.N, isCtrlPressed = true))
        assertEquals(SearchWindowKeyAction.SELECT_PREV, bubble(Key.P, isCtrlPressed = true))
        assertNull(bubble(Key.N))
        assertNull(bubble(Key.P))
    }

    @Test
    fun `other keys are not handled`() {
        assertNull(bubble(Key.A))
        assertNull(bubble(Key.DirectionDown))
    }
}
