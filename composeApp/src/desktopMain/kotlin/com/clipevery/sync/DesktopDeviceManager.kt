package com.clipevery.sync

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf

class DesktopDeviceManager : DeviceManager {

    private var _searching = mutableStateOf(true)

    override val isSearching: State<Boolean> get() = _searching
}
