package com.clipevery.app

interface WindowManager {

    fun getCurrentActiveApp(): String?

    suspend fun bringToFront(windowTitle: String)

    suspend fun bringToBack(
        windowTitle: String,
        toPaste: Boolean,
    )
}
