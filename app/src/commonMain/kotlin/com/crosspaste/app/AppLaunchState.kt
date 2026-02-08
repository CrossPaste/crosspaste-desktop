package com.crosspaste.app

interface AppLaunchState {

    val acquiredLock: Boolean

    val firstLaunch: Boolean
}
