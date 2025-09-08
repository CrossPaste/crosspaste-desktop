package com.crosspaste.utils

expect fun getRtfUtils(): RtfUtils

interface RtfUtils {

    fun getText(rtf: String): String?

    fun rtfToHtml(rtf: String): String?
}
