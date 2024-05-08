package com.clipevery.app

interface AppRestartService {

    fun restart(exitApplication: () -> Unit)
}
