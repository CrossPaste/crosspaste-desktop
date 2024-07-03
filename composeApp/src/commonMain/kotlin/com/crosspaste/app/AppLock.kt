package com.crosspaste.app

interface AppLock {

    fun acquireLock(): Pair<Boolean, Boolean>

    fun releaseLock()
}
