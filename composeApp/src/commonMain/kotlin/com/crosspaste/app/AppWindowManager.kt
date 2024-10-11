package com.crosspaste.app

import com.crosspaste.utils.mainDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.Path

interface AppWindowManager {

    val firstLaunchCompleted: StateFlow<Boolean>

    val showMainDialog: StateFlow<Boolean>

    val showFileDialog: StateFlow<Boolean>

    fun doLongTaskInMain(
        scope: CoroutineScope,
        task: suspend () -> Unit,
        success: () -> Unit = {},
        fail: (Throwable) -> Unit = {},
    ) {
        setMainCursorWait()
        scope.launch {
            try {
                task()
                withContext(mainDispatcher) {
                    resetMainCursor()
                    success()
                }
            } catch (e: Throwable) {
                withContext(mainDispatcher) {
                    resetMainCursor()
                    fail(e)
                }
            }
        }
    }

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
