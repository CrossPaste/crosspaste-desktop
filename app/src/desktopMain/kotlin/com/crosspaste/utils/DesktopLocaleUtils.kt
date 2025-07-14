package com.crosspaste.utils

import com.crosspaste.i18n.DesktopGlobalCopywriter.Companion.ZH
import com.crosspaste.i18n.DesktopGlobalCopywriter.Companion.ZH_HANT
import java.util.Locale

object DesktopLocaleUtils : LocaleUtils {

    private val locale = Locale.getDefault()

    override fun getCountry(): String = locale.country

    override fun getLanguage(): String {
        val language = locale.language

        return if (!language.startsWith(ZH)) {
            language
        } else {
            detectChineseVariant()
        }
    }

    private fun detectChineseVariant(): String {
        val country = locale.country.uppercase()
        when (country) {
            "CN", "SG" -> return ZH
            "TW", "HK", "MO" -> return ZH_HANT
        }

        runCatching {
            val script = locale.script
            when (script) {
                "Hans" -> return ZH
                "Hant" -> return ZH_HANT
            }
        }

        val languageTag = locale.toLanguageTag()
        when {
            languageTag.contains("Hans") -> return ZH
            languageTag.contains("Hant") -> return ZH_HANT
        }

        val variant = locale.variant
        if (variant.isNotEmpty()) {
            when (variant.uppercase()) {
                "TRADITIONAL", "TRAD" -> return ZH_HANT
                "SIMPLIFIED", "SIMP" -> return ZH
            }
        }

        return ZH
    }

    override fun getLanguageTag(): String = locale.toLanguageTag()

    override fun getDisplayName(): String = locale.displayName
}
