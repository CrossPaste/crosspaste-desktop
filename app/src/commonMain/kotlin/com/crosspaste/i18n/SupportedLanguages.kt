package com.crosspaste.i18n

/**
 * Single source of truth for the set of UI languages the app ships translations for.
 * Lives in commonMain so Desktop and Mobile platform implementations cannot drift out
 * of sync — adding a new language here makes it visible to every platform copywriter
 * that consumes [LANGUAGE_LIST].
 */
object SupportedLanguages {

    const val DE = "de"

    const val EN = "en"

    const val ES = "es"

    const val FA = "fa"

    const val FR = "fr"

    const val KO = "ko"

    const val JA = "ja"

    const val PT = "pt"

    const val ZH = "zh"

    const val ZH_HANT = "zh_hant"

    val LANGUAGE_LIST = listOf(DE, EN, ES, FA, FR, KO, JA, PT, ZH, ZH_HANT)
}
