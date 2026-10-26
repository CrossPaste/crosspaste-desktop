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
    fun `right leaves the search input for the tag bar only with the caret at the end`() {
        val right = SearchKeyStroke(Key.DirectionRight)
        assertEquals(SearchWindowKeyAction.FOCUS_TAG_BAR, preview(FocusedElement.SEARCH_INPUT, right))
        assertNull(preview(FocusedElement.SEARCH_INPUT, right, isSearchCaretAtEnd = false))
        // Shift+Right extends a selection, it must stay with the text field
        assertNull(preview(FocusedElement.SEARCH_INPUT, right.copy(isShiftPressed = true)))
        assertNull(preview(FocusedElement.SEARCH_INPUT, right.copy(isMetaPressed = true)))
    }

    @Test
    fun `tab cycles search input, tag bar and paste list`() {
        val tab = SearchKeyStroke(Key.Tab)
        assertEquals(SearchWindowKeyAction.FOCUS_TAG_BAR, preview(FocusedElement.SEARCH_INPUT, tab))
        assertEquals(SearchWindowKeyAction.FOCUS_PASTE_LIST, preview(FocusedElement.TAG_BAR, tab))
        assertEquals(SearchWindowKeyAction.FOCUS_SEARCH_INPUT, preview(FocusedElement.PASTE_LIST, tab))
    }

    @Test
    fun `shift tab cycles backwards`() {
        val shiftTab = SearchKeyStroke(Key.Tab, isShiftPressed = true)
        assertEquals(SearchWindowKeyAction.FOCUS_PASTE_LIST, preview(FocusedElement.SEARCH_INPUT, shiftTab))
        assertEquals(SearchWindowKeyAction.FOCUS_SEARCH_INPUT, preview(FocusedElement.TAG_BAR, shiftTab))
        assertEquals(SearchWindowKeyAction.FOCUS_TAG_BAR, preview(FocusedElement.PASTE_LIST, shiftTab))
    }

    @Test
    fun `tab is left alone outside the zones and with other modifiers`() {
        assertNull(preview(FocusedElement.NONE, Key.Tab))
        assertNull(preview(FocusedElement.SEARCH_INPUT, SearchKeyStroke(Key.Tab, isCtrlPressed = true)))
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
    fun `keys the tag bar declines never reach the paste list shortcuts`() {
        assertNull(bubble(Key.Enter, zone = FocusedElement.TAG_BAR))
        assertNull(bubble(Key.DirectionUp, zone = FocusedElement.TAG_BAR))
        assertNull(bubble(Key.N, isCtrlPressed = true, zone = FocusedElement.TAG_BAR))
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
