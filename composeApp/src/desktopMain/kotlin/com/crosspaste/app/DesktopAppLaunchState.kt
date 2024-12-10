package com.crosspaste.app

data class DesktopAppLaunchState(
    val pid: Long,
    val acquireLock: Boolean,
    val firstLaunch: Boolean,
    var accessibilityPermissions: Boolean,
    var installFrom: String?,
) : AppLaunchState

const val MICROSOFT_STORE = "Microsoft Store"
