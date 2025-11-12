package com.crosspaste.platform.linux.api

import com.sun.jna.Library
import com.sun.jna.Native

interface CLocaleLibrary : Library {
    fun setlocale(
        category: Int,
        locale: String,
    ): String

    companion object {
        val INSTANCE: CLocaleLibrary = Native.load("c", CLocaleLibrary::class.java)

        fun setCLocale() {
            val LC_ALL = 0 // LC_ALL category
            val locale = "C"

            // Call setlocale to set the locale to "C"
            val result = INSTANCE.setlocale(LC_ALL, locale)
        }
    }
}
