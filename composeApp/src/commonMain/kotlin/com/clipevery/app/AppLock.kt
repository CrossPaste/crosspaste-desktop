package com.clipevery.app

interface AppLock {

    fun acquireLock(): Boolean

    fun releaseLock()
}
