package com.crosspaste.image

import com.crosspaste.module.ServiceModule
import okio.Path

interface OCRModule : ServiceModule {

    suspend fun addLanguage(language: String)

    suspend fun removeLanguage(language: String)

    suspend fun extractText(path: Path): Result<String>
}
