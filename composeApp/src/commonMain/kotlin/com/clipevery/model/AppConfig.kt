package com.clipevery.model

import kotlinx.serialization.Serializable
import java.util.Locale

@Serializable
data class AppConfig(
    val bindingState: Boolean = false,
    val language: String = Locale.getDefault().language,)
