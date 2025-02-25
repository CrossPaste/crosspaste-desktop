package com.crosspaste.app

class GeneralAppLockState(
    override val acquiredLock: Boolean,
    override val firstLaunch: Boolean,
) : AppLockState
