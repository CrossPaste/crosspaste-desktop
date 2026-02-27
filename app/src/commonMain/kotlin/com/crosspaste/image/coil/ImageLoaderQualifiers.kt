package com.crosspaste.image.coil

import org.koin.core.qualifier.named

object ImageLoaderQualifiers {
    val GENERATE_IMAGE = named("generateImageLoader")
    val FAVICON = named("faviconImageLoader")
    val APP_SOURCE = named("appSourceLoader")
    val USER_IMAGE = named("userImageLoader")
}
