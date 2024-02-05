package com.clipevery.net

import androidx.compose.runtime.State

interface DeviceRefresher {

    val isRefreshing: State<Boolean>

    fun refresh(checkAction: CheckAction)
}
