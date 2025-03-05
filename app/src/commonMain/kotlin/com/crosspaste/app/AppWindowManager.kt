package com.crosspaste.app

import com.crosspaste.ui.ScreenContext
import com.crosspaste.ui.ScreenType
import com.crosspaste.utils.mainDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.Path

interface AppWindowManager {

    val screenContext: StateFlow<ScreenContext>

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
        fileSelectionMode: FileSelectionMode,
        title: String? = null,
        initPath: Path? = null,
        cancel: (() -> Unit)? = null,
        action: (Path) -> Unit,
    )

    fun setScreen(screenContext: ScreenContext)

    fun returnScreen()

    fun toScreen(
        screenType: ScreenType,
        context: Any = Unit,
    )
}

enum class FileSelectionMode {
    FILE_ONLY,
    DIRECTORY_ONLY,
    FILES_AND_DIRECTORIES,
}
