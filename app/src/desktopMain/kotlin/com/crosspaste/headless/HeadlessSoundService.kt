package com.crosspaste.headless

import com.crosspaste.sound.SoundService

class HeadlessSoundService : SoundService {

    override fun errorSound() {
        // No-op in headless mode
    }

    override fun successSound() {
        // No-op in headless mode
    }

    override fun enablePasteboardListening() {
        // No-op in headless mode
    }

    override fun disablePasteboardListening() {
        // No-op in headless mode
    }
}
