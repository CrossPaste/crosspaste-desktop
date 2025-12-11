package com.crosspaste.image.coil

import com.crosspaste.platform.Platform

object CropTransformationFactory {
    fun create(
        currentPlatform: Platform,
        syncPlatform: Platform?,
    ): MacCropTransformation? =
        if ((syncPlatform ?: currentPlatform).isMacos()) {
            // macOS specific crop values
            MacCropTransformation
        } else {
            null
        }
}
