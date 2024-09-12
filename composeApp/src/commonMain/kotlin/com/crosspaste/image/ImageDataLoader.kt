package com.crosspaste.image

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Density
import com.crosspaste.paste.item.PasteFileCoordinate
import okio.Path

interface ImageDataLoader {

    fun loadImageData(
        pasteFileCoordinate: PasteFileCoordinate,
        density: Density,
        createThumbnail: Boolean = false,
    ): LoadStateData

    fun loadImageData(
        path: Path,
        density: Density,
    ): LoadStateData

    fun loadIconData(
        isFile: Boolean?,
        density: Density,
    ): LoadStateData

    @Composable
    fun loadPasteType(pasteType: Int): ImageData<Painter>
}
