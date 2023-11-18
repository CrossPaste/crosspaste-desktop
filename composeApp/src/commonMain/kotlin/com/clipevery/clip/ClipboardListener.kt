package com.clipevery.clip

import java.util.EventListener


interface ClipboardListener : EventListener {
    fun onEvent(event: ClipboardEvent?)
}