package com.crosspaste.image

import com.crosspaste.module.ServiceModule
import okio.Path

interface OCRModule : ServiceModule {

    fun addLanguage(language: String)

    fun removeLanguage(language: String)

    fun extractText(path: Path): Result<String>
}
