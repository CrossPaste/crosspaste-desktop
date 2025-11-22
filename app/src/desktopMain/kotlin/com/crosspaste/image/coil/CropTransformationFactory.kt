package com.crosspaste.image.coil

import coil3.transform.Transformation
import com.crosspaste.platform.Platform

object CropTransformationFactory {
    fun create(
        currentPlatform: Platform,
        syncPlatform: Platform?,
    ): List<Transformation> =
        if ((syncPlatform ?: currentPlatform).isMacos()) {
            // macOS specific crop values
            listOf(
                MacCropTransformation,
            )
        } else {
            listOf()
        }
}
