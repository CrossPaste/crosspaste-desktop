package com.crosspaste.utils

actual fun getRtfUtils(): RtfUtils = NativeRtfUtils

object NativeRtfUtils : RtfUtils {

    override fun getRtfText(rtf: String): String {
        // Native implementation for parsing RTF text
        // This is a placeholder; actual implementation will depend on the native platform capabilities
        throw NotImplementedError("RTF parsing not implemented for native platform")
    }
}
