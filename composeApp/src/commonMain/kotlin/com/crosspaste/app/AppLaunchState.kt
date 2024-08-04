package com.crosspaste.app

data class AppLaunchState(
    val pid: Long,
    val acquireLock: Boolean,
    val firstLaunch: Boolean,
    var accessibilityPermissions: Boolean,
    var installFrom: String?,
)

const val MICROSOFT_STORE = "Microsoft Store"
