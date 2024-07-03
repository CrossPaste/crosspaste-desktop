package com.crosspaste.app

interface AppRestartService {

    fun restart(exitApplication: () -> Unit)
}
