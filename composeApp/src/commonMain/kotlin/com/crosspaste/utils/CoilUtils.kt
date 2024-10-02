package com.crosspaste.utils

import coil3.PlatformContext

expect fun getCoilUtils(): CoilUtils

interface CoilUtils {

    fun getCoilContext(): PlatformContext
}
