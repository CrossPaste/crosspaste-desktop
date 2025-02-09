package com.crosspaste.app

interface AppLock {

    fun acquireLock(): AppLockState

    fun releaseLock()

    fun resetFirstLaunchFlag()
}
