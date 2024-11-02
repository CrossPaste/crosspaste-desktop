package com.crosspaste.utils

expect fun getColorUtils(): ColorUtils

interface ColorUtils {

    fun tryCovertToColor(text: String): Long?
}
