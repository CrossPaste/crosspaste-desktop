package com.crosspaste.ui.base

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import com.crosspaste.image.IconKey
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.utils.getCoilUtils
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache

class DesktopIconStyle(
    private val userDataPathProvider: UserDataPathProvider,
) : IconStyle {

    private val coilUtils = getCoilUtils()

    private val iconStyleCache: LoadingCache<IconKey, Boolean> =
        Caffeine
            .newBuilder()
            .maximumSize(1000)
            .build { key ->
                userDataPathProvider.findIconPath(key.appInstanceId, key.source)?.let { iconPath ->
                    val imageBitmap =
                        coilUtils
                            .createBitmap(iconPath)
                            .asComposeImageBitmap()
                    checkMacStyleIcon(imageBitmap)
                } ?: false
            }

    override fun isMacStyleIcon(
        source: String,
        appInstanceId: String?,
    ): Boolean = iconStyleCache.get(IconKey(source, appInstanceId))

    override fun refreshStyle(
        source: String,
        appInstanceId: String?,
    ) {
        iconStyleCache.refresh(IconKey(source, appInstanceId))
    }

    private fun checkMacStyleIcon(imageBitmap: ImageBitmap): Boolean {
        val width = imageBitmap.width
        val height = imageBitmap.height
        // Width of the border is 1/12th of the image width
        val edgeWidth = width / 12
        // Define sampling rate, at least 1, meaning check at least every 5 pixels
        val sampleRate = maxOf(1, edgeWidth / 5)

        val pixelMap = imageBitmap.toPixelMap() // Convert once to avoid multiple conversions

        fun isTransparent(
            x: Int,
            y: Int,
        ): Boolean {
            val pixel = pixelMap[x, y]
            return pixel.alpha <= 0.01f
        }

        // Function to check the edge, taking start and end coordinates, and whether the check is horizontal or vertical
        fun checkEdge(
            start: Int,
            end: Int,
            isHorizontal: Boolean,
        ): Boolean {
            for (i in start until end step sampleRate) {
                if (isHorizontal) {
                    // Horizontal check, fix the y coordinate, change the x coordinate
                    for (y in 0 until edgeWidth step sampleRate) {
                        if (!isTransparent(i, y) || !isTransparent(i, height - 1 - y)) return false
                    }
                } else {
                    // Vertical check, fix the x coordinate, change the y coordinate
                    for (x in 0 until edgeWidth step sampleRate) {
                        if (!isTransparent(x, i) || !isTransparent(width - 1 - x, i)) return false
                    }
                }
            }
            return true
        }

        // Check all four edges
        return checkEdge(0, width, isHorizontal = true) && checkEdge(0, height, isHorizontal = false)
    }
}
