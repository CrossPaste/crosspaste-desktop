package com.crosspaste.app

import com.crosspaste.ui.ScreenContext
import com.crosspaste.ui.ScreenType
import com.crosspaste.utils.mainDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface AppWindowManager {

    val screenContext: StateFlow<ScreenContext>

    val firstLaunchCompleted: StateFlow<Boolean>

    val showMainDialog: StateFlow<Boolean>

    fun doLongTaskInMain(
        scope: CoroutineScope,
        task: suspend () -> Result<Unit?>,
        success: () -> Unit = {},
        fail: (Throwable) -> Unit = {},
    ) {
        setMainCursorWait()
        scope.launch {
            val result = task()
            withContext(mainDispatcher) {
                resetMainCursor()
                result.onSuccess { success() }
                    .onFailure { fail(it) }
            }
        }
    }

    fun setMainCursorWait()

    fun resetMainCursor()

    fun resetSearchCursor()

    fun setSearchCursorWait()

    suspend fun toPaste()

    fun setScreen(screenContext: ScreenContext)

    fun returnScreen()

    fun toScreen(
        screenType: ScreenType,
        context: Any = Unit,
    )
}
