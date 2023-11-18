package com.clipevery.clip

import java.io.Serial
import java.util.EventObject

class ClipboardEvent
    (source: Any?) : EventObject(source) {
    companion object {
        @Serial
        private val serialVersionUID = 6354639749124932240L
    }
}