package com.crosspaste.image

abstract class PlatformImage(
    protected val data: ByteArray,
) {

    abstract fun toImage(
        width: Int,
        height: Int,
    ): Any
}
