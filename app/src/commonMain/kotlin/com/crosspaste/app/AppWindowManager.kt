package com.crosspaste.app

import com.crosspaste.ui.Pasteboard
import com.crosspaste.ui.ScreenContext
import com.crosspaste.ui.ScreenType
import com.crosspaste.utils.mainDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

abstract class AppWindowManager {

    private val _screenContext = MutableStateFlow(ScreenContext(Pasteboard))

    val screenContext: StateFlow<ScreenContext> = _screenContext

    private val _showMainDialog = MutableStateFlow(false)

    val showMainDialog: StateFlow<Boolean> = _showMainDialog

    fun doLongTaskInMain(
        scope: CoroutineScope,
        task: suspend () -> Result<Unit?>,
        success: () -> Unit = {},
        fail: (Throwable) -> Unit = {},
    ) {
        scope.launch {
            val result = task()
            withContext(mainDispatcher) {
                result
                    .onSuccess { success() }
                    .onFailure { fail(it) }
            }
        }
    }

    abstract suspend fun toPaste()

    fun returnScreen() {
        setScreen(screenContext.value.returnNext())
    }

    fun setScreen(screenContext: ScreenContext) {
        _screenContext.value = screenContext
    }

    fun toScreen(
        screenType: ScreenType,
        context: Any = Unit,
    ) {
        setScreen(
            if (context == Unit) {
                ScreenContext(screenType, _screenContext.value)
            } else {
                ScreenContext(screenType, _screenContext.value, context)
            },
        )
    }
}
