package com.clipevery.clip

interface ClipboardMonitor {
    fun start()

    fun stop()

    fun onChange(event: ClipboardEvent?)
}