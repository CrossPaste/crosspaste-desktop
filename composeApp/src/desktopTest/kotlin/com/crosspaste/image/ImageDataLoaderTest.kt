package com.crosspaste.image

import androidx.compose.ui.unit.Density
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertTrue

class ImageDataLoaderTest {

    private val imageDataLoader = getImageDataLoader()

    private val density = Density(1f, 1f)

    @Test
    fun testLoadImage() {
        val crosspasteIconPath =
            this::class.java.classLoader
                .getResource("crosspaste_icon.png")?.path?.toPath()!!

        val crosspasteIconData = imageDataLoader.loadImageData(crosspasteIconPath, density)

        assertTrue(crosspasteIconData.isSuccess() && crosspasteIconData is ImageData<*>)

        assertTrue(!crosspasteIconData.isIcon)

        val fileSvgPath =
            this::class.java.classLoader
                .getResource("icon/paste/file.svg")?.path?.toPath()!!

        val fileSvgData = imageDataLoader.loadImageData(fileSvgPath, density)

        assertTrue(fileSvgData.isSuccess() && fileSvgData is ImageData<*>)

        assertTrue(fileSvgData.isIcon)
    }
}
