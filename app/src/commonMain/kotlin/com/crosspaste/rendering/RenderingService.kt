package com.crosspaste.rendering

import okio.Path

interface RenderingService<T> {

    suspend fun saveRenderImage(
        input: T,
        savePath: Path,
    )

    fun start()

    fun stop()

    fun restart() {
        stop()
        start()
    }
}
