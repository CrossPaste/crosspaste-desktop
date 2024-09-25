package com.crosspaste.image

import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.res.loadImageBitmap
import okio.Path
import org.jetbrains.skia.Bitmap

class DesktopImageCreator : ImageCreator {
    override fun createBitmap(path: Path): Bitmap {
        return path.toFile().inputStream().buffered().use {
            it.use(::loadImageBitmap).asSkiaBitmap()
        }
    }
}
