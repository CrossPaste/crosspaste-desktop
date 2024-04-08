package com.clipevery.clip

import com.clipevery.app.AppUI

class DesktopClipSearchService(private val appUI: AppUI) : ClipSearchService {

    private var start: Boolean = false

    @Synchronized
    override fun tryStart(): Boolean {
        if (!start) {
            start = true
            return true
        } else {
            return false
        }
    }

    override fun stop() {
        if (start) {
            start = false
            // todo stop
        }
    }

    override fun getAppUI(): AppUI {
        return appUI
    }
}
