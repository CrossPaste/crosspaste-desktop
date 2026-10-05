package com.crosspaste.ui.paste.edit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

private const val MAX_HISTORY_SIZE = 50

/** Bounded undo/redo history for the plain-text editor. */
class TextEditHistory(
    initialText: String,
) {
    private var history by mutableStateOf(listOf(initialText))
    private var index by mutableStateOf(0)

    val text: String get() = history[index]
    val canUndo: Boolean get() = index > 0
    val canRedo: Boolean get() = index < history.size - 1

    fun push(newText: String) {
        if (newText == text) return
        history = (history.subList(0, index + 1) + newText).takeLast(MAX_HISTORY_SIZE)
        index = history.size - 1
    }

    fun undo() {
        if (canUndo) index -= 1
    }

    fun redo() {
        if (canRedo) index += 1
    }
}
