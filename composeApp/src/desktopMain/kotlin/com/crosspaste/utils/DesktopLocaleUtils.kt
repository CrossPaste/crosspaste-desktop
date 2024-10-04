package com.crosspaste.utils

import java.util.Locale

object DesktopLocaleUtils : LocaleUtils {

    private val locale = Locale.getDefault()

    override fun getCountry(): String {
        return locale.country
    }

    override fun getLanguage(): String {
        return locale.language
    }

    override fun getDisplayName(): String {
        return locale.displayName
    }
}
