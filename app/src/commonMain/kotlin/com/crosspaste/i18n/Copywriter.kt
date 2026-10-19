package com.crosspaste.i18n

import com.crosspaste.utils.DateTimeFormatOptions
import kotlinx.coroutines.flow.StateFlow
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

    /**
     * The active language code, updated synchronously by [switchLanguage]. Lets non-Compose
     * observers (native shells, services) react to language changes.
     */
    val languageFlow: StateFlow<String>

    fun switchLanguage(language: String)

    fun getAllLanguages(): List<Language>
}

data class Language(
    val abridge: String,
    val name: String,
)
