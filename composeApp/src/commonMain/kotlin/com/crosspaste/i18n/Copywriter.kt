package com.crosspaste.i18n

import kotlinx.datetime.LocalDateTime

interface Copywriter {

    fun language(): String

    fun getText(id: String): String

    fun getDate(
        date: LocalDateTime,
        detail: Boolean = false,
    ): String

    fun getAbridge(): String
}

interface GlobalCopywriter : Copywriter {

    fun switchLanguage(language: String)

    fun getAllLanguages(): List<Language>
}

data class Language(val abridge: String, val name: String)
