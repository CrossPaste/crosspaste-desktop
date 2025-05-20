package com.crosspaste.app

data class DesktopAppLaunchState(
    val pid: Long,
    override val acquireLock: Boolean,
    override val firstLaunch: Boolean,
    var accessibilityPermissions: Boolean,
    var installFrom: String?,
) : AppLaunchState

const val MICROSOFT_STORE = "Microsoft Store"
