package com.crosspaste.app

interface AppLock {

    fun acquireLock(): AppLockState

    fun releaseLock()

    fun resetFirstLaunchFlag()
}

data class AppLockState(val acquiredLock: Boolean, val firstLaunch: Boolean)
