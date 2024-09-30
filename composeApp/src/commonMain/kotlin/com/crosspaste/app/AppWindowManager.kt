package com.crosspaste.app

import kotlinx.coroutines.flow.StateFlow
import okio.Path

interface AppWindowManager {

    val firstLaunchCompleted: StateFlow<Boolean>

    val showMainDialog: StateFlow<Boolean>

    val showFileDialog: StateFlow<Boolean>

    fun setMainCursorWait()

    fun resetMainCursor()

    fun resetSearchCursor()

    fun setSearchCursorWait()

    suspend fun toPaste()

    fun openFileChooser(
        fileChooserTitle: String,
        currentStoragePath: String,
        action: (Path) -> Unit,
        errorAction: (String) -> Unit,
    )
}
