package com.crosspaste.ui.base

import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale

interface ImageDisplayStrategy {

    /**
     * Calculates the display strategy based on the source image size and the target
     * display area size.
     *
     * @param srcSize The dimensions of the source image.
     * @param dstSize The dimensions of the destination display area.
     * @return A [DisplayResult] that contains the chosen [ContentScale] and [Alignment].
     */
    fun compute(
        srcSize: Size,
        dstSize: Size,
    ): DisplayResult

    /**
     * Result of the display strategy calculation.
     *
     * @property contentScale How the image should be scaled.
     * @property alignment     How the image should be aligned within its container.
     */
    data class DisplayResult(
        val contentScale: ContentScale,
        val alignment: Alignment = Alignment.Center,
    )
}
