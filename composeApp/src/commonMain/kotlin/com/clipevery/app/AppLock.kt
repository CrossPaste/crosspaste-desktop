package com.clipevery.app

interface AppLock {

    fun acquireLock(): Pair<Boolean, Boolean>

    fun releaseLock()
}
