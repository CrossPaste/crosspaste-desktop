package com.crosspaste.utils

expect fun getRtfUtils(): RtfUtils

interface RtfUtils {

    fun getRtfText(rtf: String): String
}
