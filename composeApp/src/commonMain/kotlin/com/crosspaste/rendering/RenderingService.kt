package com.crosspaste.rendering

import okio.Path

interface RenderingService<T> {

    fun saveRenderImage(
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
