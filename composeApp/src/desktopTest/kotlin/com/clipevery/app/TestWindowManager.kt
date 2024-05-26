package com.clipevery.app

object TestWindowManager : WindowManager {

    var prevApp: String? = null

    var currentApp: String? = null

    var pasterId: Int = 0

    override fun getPrevAppName(): String? {
        return prevApp
    }

    override fun getCurrentActiveAppName(): String? {
        return currentApp
    }

    override suspend fun bringToFront(windowTitle: String) {
        if (currentApp != "Clipevery") {
            prevApp = currentApp
        }
        currentApp = "Clipevery"
    }

    override suspend fun bringToBack(
        windowTitle: String,
        toPaste: Boolean,
    ) {
        currentApp = prevApp
        if (toPaste) {
            toPaste()
        }
    }

    override suspend fun toPaste() {
        pasterId++
    }

    fun init() {
        prevApp = null
        currentApp = null
        pasterId = 0
    }

    fun activeApp(appName: String) {
        currentApp = appName
    }
}
