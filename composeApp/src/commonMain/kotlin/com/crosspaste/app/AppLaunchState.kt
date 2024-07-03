package com.crosspaste.app

data class AppLaunchState(
    val acquireLock: Boolean,
    val firstLaunch: Boolean,
    var accessibilityPermissions: Boolean,
)
