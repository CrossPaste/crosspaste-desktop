package com.crosspaste.app

data class DesktopAppLaunchState(
    val pid: Long,
    override val acquiredLock: Boolean,
    override val firstLaunch: Boolean,
    var accessibilityPermissions: Boolean,
    val installFrom: String?,
) : AppLaunchState

const val MICROSOFT_STORE = "Microsoft Store"
