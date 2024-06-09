package com.clipevery.net

interface SyncRefresher {

    val refreshing: Boolean

    fun refresh()
}
