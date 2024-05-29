package com.clipevery.net

import androidx.compose.runtime.State

interface SyncRefresher {

    val isRefreshing: State<Boolean>

    fun refresh()
}
