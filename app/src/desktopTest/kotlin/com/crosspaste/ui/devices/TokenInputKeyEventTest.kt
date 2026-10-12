package com.crosspaste.ui.devices

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(InternalComposeUiApi::class)
class TokenInputKeyEventTest {
    @Test
    fun `enter and numpad enter confirm once per press and release`() {
        for (key in listOf(Key.Enter, Key.NumPadEnter)) {
            var confirms = 0
            var cancels = 0
            for (type in listOf(KeyEventType.KeyDown, KeyEventType.KeyUp)) {
                assertTrue(handleKeyEvent(KeyEvent(key, type), { confirms++ }, { cancels++ }))
            }
            assertEquals(1, confirms)
            assertEquals(0, cancels)
        }
    }

    @Test
    fun `escape cancels once without confirming`() {
        var confirms = 0
        var cancels = 0
        for (type in listOf(KeyEventType.KeyDown, KeyEventType.KeyUp)) {
            assertTrue(handleKeyEvent(KeyEvent(Key.Escape, type), { confirms++ }, { cancels++ }))
        }
        assertEquals(0, confirms)
        assertEquals(1, cancels)
    }

    @Test
    fun `other keys remain available to the text field`() {
        var actions = 0
        for (key in listOf(Key.One, Key.Backspace, Key.Tab)) {
            assertFalse(handleKeyEvent(KeyEvent(key, KeyEventType.KeyDown), { actions++ }, { actions++ }))
        }
        assertEquals(0, actions)
    }
}
