package com.clipevery.app

interface AppLock {

    // The first boolean value indicates whether the file lock is obtained
    // the second boolean value indicates whether this application is started for the first time.
    fun acquireLock(): Pair<Boolean, Boolean>

    fun releaseLock()
}
