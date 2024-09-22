package com.crosspaste.sound

interface SoundService {

    fun errorSound()

    fun successSound()

    fun enablePasteboardListening()

    fun disablePasteboardListening()
}
