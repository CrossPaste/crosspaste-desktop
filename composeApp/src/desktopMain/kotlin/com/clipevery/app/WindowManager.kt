package com.clipevery.app

interface WindowManager {

    suspend fun bringToFront(windowTitle: String)

    suspend fun bringToBack(
        windowTitle: String,
        toPaste: Boolean,
    )
}
