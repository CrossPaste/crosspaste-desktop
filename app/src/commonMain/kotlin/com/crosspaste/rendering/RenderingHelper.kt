package com.crosspaste.rendering

interface RenderingHelper {

    var scale: Double

    var dimension: RenderingDimension

    fun refresh()
}

data class RenderingDimension(
    val width: Int,
    val height: Int,
)
