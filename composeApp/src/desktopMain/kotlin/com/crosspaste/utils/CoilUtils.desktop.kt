package com.crosspaste.utils

import coil3.PlatformContext

actual fun getCoilUtils(): CoilUtils {
    return DesktopCoilUtils
}

object DesktopCoilUtils : CoilUtils {
    override fun getCoilContext(): PlatformContext {
        return PlatformContext.INSTANCE
    }
}
