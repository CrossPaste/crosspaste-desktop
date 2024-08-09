package com.crosspaste.app

object DesktopAppExitService : AppExitService {
    override val beforeExitList: MutableList<() -> Unit> = mutableListOf()
    override val beforeReleaseLockList: MutableList<() -> Unit> = mutableListOf()
}
