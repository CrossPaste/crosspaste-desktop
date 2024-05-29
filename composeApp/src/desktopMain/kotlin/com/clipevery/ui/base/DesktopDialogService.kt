package com.clipevery.ui.base

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class DesktopDialogService : DialogService {

    override var dialog: ClipDialog? by mutableStateOf(null)
}
