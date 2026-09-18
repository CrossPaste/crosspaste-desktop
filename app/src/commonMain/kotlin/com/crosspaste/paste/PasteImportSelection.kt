package com.crosspaste.paste

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okio.Path

/**
 * The file currently chosen on the Import page. Lives outside the page so that
 * a file dropped anywhere on the window can be handed to the page, and so that
 * the choice survives navigating away and back.
 */
class PasteImportSelection {

    private val _path = MutableStateFlow<Path?>(null)

    val path: StateFlow<Path?> = _path

    fun select(path: Path) {
        _path.value = path
    }

    fun clear() {
        _path.value = null
    }
}
