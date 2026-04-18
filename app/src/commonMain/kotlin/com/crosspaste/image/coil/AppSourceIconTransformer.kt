package com.crosspaste.image.coil

import coil3.transform.Transformation

interface AppSourceIconTransformer {

    /**
     * Transformations to apply when loading an app source icon.
     * Platform implementations decide whether to trim padding, normalize shape, etc.
     */
    val transformations: List<Transformation>

    /**
     * Over-requested decode size compensating for pixels removed by [transformations].
     * Returning [sizePx] means no over-request.
     */
    fun requestSize(sizePx: Int): Int = sizePx
}
