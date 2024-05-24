package com.clipevery.app

interface WindowManager {

    fun getPrevAppName(): String?

    fun getCurrentActiveAppName(): String?

    suspend fun bringToFront(windowTitle: String)

    suspend fun bringToBack(
        windowTitle: String,
        toPaste: Boolean,
    )

    suspend fun toPaste()
}
