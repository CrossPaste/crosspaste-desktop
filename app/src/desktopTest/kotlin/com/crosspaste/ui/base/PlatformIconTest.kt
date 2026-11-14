package com.crosspaste.ui.base

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.renderComposeScene
import com.crosspaste.platform.Platform
import com.crosspaste.ui.theme.AppUISize.xLarge
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlatformIconTest {

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun duoUsesFoldedIconAndGenericIphoneUsesStandardIcon() {
        val duo = Platform(name = Platform.IPHONE, arch = "iPhone19,4", bitMode = 64, version = "27.1")
        val size = xLarge.value.toInt()

        renderComposeScene(width = size * 3, height = size) {
            Row {
                Icon(PlatformIcon(duo), null, Modifier.size(xLarge), tint = Color.Black)
                Icon(PlatformIcon(duo.copy(arch = "arm64")), null, Modifier.size(xLarge), tint = Color.Black)
                Icon(iphone(), null, Modifier.size(xLarge), tint = Color.Black)
            }
        }.use { image ->
            val pixels = image.toComposeImageBitmap().toPixelMap()
            val middle = size / 2

            // The Duo has a solid cover and a second chassis edge visible beneath it.
            assertEquals(1f, pixels[middle, 20].alpha)
            assertTrue(pixels[19, middle].alpha in 0.5f..0.6f)
            assertTrue(pixels[middle, 22].alpha in 0.5f..0.6f)

            // A peer reporting only arm64 must render the same icon as a standard iPhone.
            assertEquals(0f, pixels[size + middle, 4].alpha)
            assertEquals(1f, pixels[size + middle, 20].alpha)
            for (y in 0 until size) {
                for (x in 0 until size) {
                    assertEquals(pixels[2 * size + x, y], pixels[size + x, y])
                }
            }
        }
    }
}
