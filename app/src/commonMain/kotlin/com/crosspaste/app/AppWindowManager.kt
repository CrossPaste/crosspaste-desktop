package com.crosspaste.app

import com.crosspaste.utils.mainDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

abstract class AppWindowManager {

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
}
