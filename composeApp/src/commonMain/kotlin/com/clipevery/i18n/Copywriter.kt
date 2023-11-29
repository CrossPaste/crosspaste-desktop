package com.clipevery.i18n

interface Copywriter {
    fun getText(id: String): String

    fun getAbridge(): String
}

interface GlobalCopywriter: Copywriter {

    fun switchLanguage(language: String)

    fun getAllLanguages(): List<Language>

}

data class Language(val abridge: String, val name: String)