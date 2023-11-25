package com.clipevery.i18n

interface Copywriter {
    fun getText(id: String): String
}

interface GlobalCopywriter: Copywriter {

    fun switchLanguage(language: String)

}