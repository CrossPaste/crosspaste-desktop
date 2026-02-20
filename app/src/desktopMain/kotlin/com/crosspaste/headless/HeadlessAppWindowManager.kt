package com.crosspaste.headless

import com.crosspaste.app.AppWindowManager

class HeadlessAppWindowManager : AppWindowManager() {

    override suspend fun toPaste() {
        // No-op in headless mode
    }
}
