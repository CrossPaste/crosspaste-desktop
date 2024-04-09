package com.clipevery.sync

import androidx.compose.runtime.State

interface DeviceManager {

    val isSearching: State<Boolean>

}