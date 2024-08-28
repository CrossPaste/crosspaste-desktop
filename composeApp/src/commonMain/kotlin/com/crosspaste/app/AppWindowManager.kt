package com.crosspaste.app

import okio.Path

interface AppWindowManager {

    var hasCompletedFirstLaunchShow: Boolean

    var showMainDialog: Boolean

    var showFileDialog: Boolean

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
