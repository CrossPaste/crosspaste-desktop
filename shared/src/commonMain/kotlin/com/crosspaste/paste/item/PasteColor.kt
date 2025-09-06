package com.crosspaste.paste.item

interface PasteColor {

    val color: Int

    fun getRed(): Int = ((color shr 16) and 0xFF)

    fun getGreen(): Int = ((color shr 8) and 0xFF)

    fun getBlue(): Int = (color and 0xFF)

    fun getAlpha(): Int = ((color shr 24) and 0xFF)

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
