package com.crosspaste.ui.paste.edit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TextEditHistoryTest {

    @Test
    fun `undo and redo walk the history and a new edit drops the redo branch`() {
        val history = TextEditHistory("a")
        history.push("ab")
        history.push("abc")

        history.undo()
        assertEquals("ab", history.text)
        assertTrue(history.canRedo)

        history.push("abX")
        assertEquals("abX", history.text)
        assertFalse(history.canRedo)

        history.undo()
        history.undo()
        assertEquals("a", history.text)
        assertFalse(history.canUndo)
    }

    @Test
    fun `history keeps only the most recent 50 entries`() {
        val history = TextEditHistory("0")
        for (i in 1..60) {
            history.push(i.toString())
        }

        repeat(100) { history.undo() }

        assertEquals("11", history.text)
    }
}
