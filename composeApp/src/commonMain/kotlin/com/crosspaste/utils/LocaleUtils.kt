package com.crosspaste.utils

expect fun getLocaleUtils(): LocaleUtils

interface LocaleUtils {

    fun getCurrentLocale(): String
}
