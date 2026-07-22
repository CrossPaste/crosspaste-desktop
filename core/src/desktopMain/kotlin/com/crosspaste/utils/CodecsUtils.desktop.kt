package com.crosspaste.utils

actual fun getCodecsUtils(): CodecsUtils = DesktopCoreCodecsUtils

object DesktopCoreCodecsUtils : CodecsUtils
