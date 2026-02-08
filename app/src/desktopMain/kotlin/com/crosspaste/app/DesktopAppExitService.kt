package com.crosspaste.app

import java.util.concurrent.CopyOnWriteArrayList

object DesktopAppExitService : AppExitService {
    override val beforeExitList: MutableList<() -> Unit> = CopyOnWriteArrayList()
    override val beforeReleaseLockList: MutableList<() -> Unit> = CopyOnWriteArrayList()
}
