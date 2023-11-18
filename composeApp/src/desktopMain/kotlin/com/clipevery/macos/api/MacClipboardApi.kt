package com.clipevery.macos.api

import com.sun.jna.Library
import com.sun.jna.Native


interface MacClipboard : Library {
    val clipboardChangeCount: Int

    companion object {
        val INSTANCE: MacClipboard = Native.load("ClipboardHelper", MacClipboard::class.java)
    }
}