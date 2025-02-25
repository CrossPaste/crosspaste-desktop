package com.crosspaste.app

interface AppExitService {

    val beforeExitList: MutableList<() -> Unit>

    val beforeReleaseLockList: MutableList<() -> Unit>
}

enum class ExitMode {
    EXIT,
    RESTART,
    MIGRATION,
}
