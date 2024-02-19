package com.clipevery.i18n

import java.time.LocalDateTime

interface Copywriter {

    fun getText(id: String): String

    fun getDate(date: LocalDateTime): String

    fun getAbridge(): String
}

interface GlobalCopywriter: Copywriter {

    fun switchLanguage(language: String)

    fun getAllLanguages(): List<Language>

}

data class Language(val abridge: String, val name: String)

class PreviewGlobalCopywriter: GlobalCopywriter {
    override fun switchLanguage(language: String) {
        println("switchLanguage: $language")
    }

    override fun getAllLanguages(): List<Language> {
        return listOf(
            Language("en", "English"),
        )
    }

    override fun getText(id: String): String {
        return id
    }

    override fun getDate(date: LocalDateTime): String {
        return date.toString()
    }

    override fun getAbridge(): String {
        return "en"
    }
}