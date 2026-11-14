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

            // At mid-height the Duo has a solid hinge edge followed by a transparent seam.
            assertEquals(1f, pixels[4, middle].alpha)
            assertEquals(0f, pixels[6, middle].alpha)
            assertEquals(1f, pixels[8, middle].alpha)

            // A peer reporting only arm64 must render the same icon as a standard iPhone.
            assertEquals(1f, pixels[size + middle, middle].alpha)
            for (y in 0 until size) {
                for (x in 0 until size) {
                    assertEquals(pixels[2 * size + x, y], pixels[size + x, y])
                }
            }
        }
    }
}
