package com.crosspaste.sound

interface SoundService {

    fun errorSound()

    fun syncFileCompleteSound()

    fun enablePasteboardListening()

    fun disablePasteboardListening()
}
