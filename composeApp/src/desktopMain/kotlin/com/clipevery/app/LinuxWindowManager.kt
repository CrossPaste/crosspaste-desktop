package com.clipevery.app

class LinuxWindowManager : WindowManager {

    override fun getPrevAppName(): String? {
        return null
    }

    override fun getCurrentActiveAppName(): String? {
        return null
    }

    override suspend fun bringToBack(
        windowTitle: String,
        toPaste: Boolean,
    ) {
        TODO("Not yet implemented")
    }

    override suspend fun bringToFront(windowTitle: String) {
        TODO("Not yet implemented")
    }
}
