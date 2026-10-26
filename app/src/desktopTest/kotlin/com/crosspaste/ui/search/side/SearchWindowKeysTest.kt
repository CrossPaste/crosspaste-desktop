package com.crosspaste.ui.search.side

import androidx.compose.ui.input.key.Key
import com.crosspaste.ui.model.FocusedElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SearchWindowKeysTest {

    private fun preview(
        zone: FocusedElement,
        key: Key,
    ) = resolveSearchWindowKey(KeyPhase.PREVIEW, zone, key, isCtrlPressed = false)

    private fun bubble(
        key: Key,
        isCtrlPressed: Boolean = false,
        zone: FocusedElement = FocusedElement.PASTE_LIST,
    ) = resolveSearchWindowKey(KeyPhase.BUBBLE, zone, key, isCtrlPressed)

    @Test
    fun `down in the search input moves to the paste list before the text field sees it`() {
        assertEquals(SearchWindowKeyAction.FOCUS_PASTE_LIST, preview(FocusedElement.SEARCH_INPUT, Key.DirectionDown))
    }

    @Test
    fun `preview leaves keys alone outside the search input`() {
        assertNull(preview(FocusedElement.PASTE_LIST, Key.DirectionDown))
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
