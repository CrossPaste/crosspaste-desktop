package com.crosspaste.utils

import java.util.*

actual fun getLocaleUtils(): LocaleUtils {
    return DesktopLocaleUtils
}

object DesktopLocaleUtils : LocaleUtils {
    override fun getCurrentLocale(): String {
        return Locale.getDefault().language
    }
}
