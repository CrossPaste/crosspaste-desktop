package com.crosspaste.net

interface SyncRefresher {

    val refreshing: Boolean

    fun refresh()
}
