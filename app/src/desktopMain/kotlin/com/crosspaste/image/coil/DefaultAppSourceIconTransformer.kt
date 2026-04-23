package com.crosspaste.image.coil

import coil3.transform.Transformation

object DefaultAppSourceIconTransformer : AppSourceIconTransformer {

    override val transformations: List<Transformation> = listOf(AppSourceIconTrimTransformation)

    override fun requestSize(sizePx: Int): Int = AppSourceIconTrimTransformation.requestSize(sizePx)
}
