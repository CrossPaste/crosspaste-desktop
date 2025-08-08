package com.crosspaste.paste.item

interface PasteColor {

    val color: Long

    fun getRed(): Int = ((color shr 16) and 0xFF).toInt()

    fun getGreen(): Int = ((color shr 8) and 0xFF).toInt()

    fun getBlue(): Int = (color and 0xFF).toInt()

    fun getAlpha(): Int = ((color shr 24) and 0xFF).toInt()

    fun toHexString(): String =
        buildString {
            append('#')
            append(getAlpha().toString(16).padStart(2, '0').uppercase())
            append(getRed().toString(16).padStart(2, '0').uppercase())
            append(getGreen().toString(16).padStart(2, '0').uppercase())
            append(getBlue().toString(16).padStart(2, '0').uppercase())
        }

    fun toRGBString(): String = "rgb(${getRed()}, ${getGreen()}, ${getBlue()})"

    fun toRGBAString(): String = "rgba(${getRed()}, ${getGreen()}, ${getBlue()}, ${getAlpha() / 255f})"
}
