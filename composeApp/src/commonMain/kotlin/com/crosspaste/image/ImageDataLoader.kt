package com.crosspaste.image

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Density
import okio.Path

expect fun getImageDataLoader(): ImageDataLoader

interface ImageDataLoader {

    fun loadImageData(
        path: Path,
        density: Density,
        thumbnailLoader: ThumbnailLoader? = null,
    ): LoadStateData

    fun loadIconData(
        isFile: Boolean?,
        density: Density,
    ): LoadStateData

    @Composable
    fun loadPasteType(pasteType: Int): ImageData<Painter>
}
