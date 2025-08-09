package com.crosspaste.rendering

import com.crosspaste.paste.PasteData

interface RenderingService<T> {

    suspend fun render(pasteData: PasteData)

    fun start()

    fun stop()

    fun restart() {
        stop()
        start()
    }
}
