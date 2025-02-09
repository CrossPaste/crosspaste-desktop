package com.crosspaste.app

interface AppLockState {

    val acquiredLock: Boolean

    val firstLaunch: Boolean
}
