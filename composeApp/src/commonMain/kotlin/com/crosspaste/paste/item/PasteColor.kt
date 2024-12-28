package com.crosspaste.paste.item

interface PasteColor {

    var color: Long

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

    fun setFromHex(hex: String) {
        val cleanHex = hex.replace("#", "").uppercase()
        val rgb =
            when (cleanHex.length) {
                3 -> cleanHex.map { "$it$it" }.joinToString("")
                6 -> cleanHex
                else -> throw IllegalArgumentException("Invalid hex color format")
            }

        val r = rgb.substring(0, 2).toInt(16)
        val g = rgb.substring(2, 4).toInt(16)
        val b = rgb.substring(4, 6).toInt(16)
        setFromRGB(r, g, b)
    }

    fun setFromRGB(
        r: Int,
        g: Int,
        b: Int,
        a: Int = 255,
    ) {
        require(r in 0..255 && g in 0..255 && b in 0..255 && a in 0..255) {
            "RGB values must be between 0 and 255"
        }
        color = ((a.toLong() and 0xFF) shl 24) or
            ((r.toLong() and 0xFF) shl 16) or
            ((g.toLong() and 0xFF) shl 8) or
            (b.toLong() and 0xFF)
    }
}
