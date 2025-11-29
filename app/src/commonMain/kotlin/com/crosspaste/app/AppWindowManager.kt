package com.crosspaste.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

abstract class AppWindowManager {

    private val _showMainDialog = MutableStateFlow(false)

    val showMainDialog: StateFlow<Boolean> = _showMainDialog

    abstract suspend fun toPaste()
}
