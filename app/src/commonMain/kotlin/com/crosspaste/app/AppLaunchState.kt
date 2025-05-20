package com.crosspaste.app

interface AppLaunchState {

    val acquireLock: Boolean

    val firstLaunch: Boolean
}
