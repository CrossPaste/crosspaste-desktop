package com.crosspaste.image.coil

import coil3.transform.Transformation

object DesktopAppSourceIconTransformer : AppSourceIconTransformer {

    override val transformations: List<Transformation> = listOf(AppSourceIconCropTransformation)

    override fun requestSize(sizePx: Int): Int = AppSourceIconCropTransformation.requestSize(sizePx)
}
