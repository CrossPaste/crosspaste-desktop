package com.crosspaste.image

abstract class PlatformImage(
    protected val data: ByteArray,
    protected val width: Int,
    protected val height: Int,
) {

    abstract fun toImage(): Any
}
