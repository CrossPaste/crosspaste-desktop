package com.crosspaste.i18n

import com.crosspaste.utils.DateTimeFormatOptions
import kotlinx.datetime.LocalDateTime

interface Copywriter {

    fun language(): String

    fun getText(
        id: String,
        vararg args: Any?,
    ): String

    fun getKeys(): Set<String>

    fun getDate(
        date: LocalDateTime,
        options: DateTimeFormatOptions = DateTimeFormatOptions(),
    ): String

    fun getAbridge(): String
}

interface GlobalCopywriter : Copywriter {

    fun switchLanguage(language: String)

    fun getAllLanguages(): List<Language>
}

data class Language(
    val abridge: String,
    val name: String,
)
