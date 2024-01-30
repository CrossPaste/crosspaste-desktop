package com.clipevery.config

import kotlinx.serialization.Serializable
import java.util.Locale
import java.util.UUID

@Serializable
data class AppConfig(
    val appInstanceId: String = UUID.randomUUID().toString(),
    val language: String = Locale.getDefault().language,
    val isFollowSystemTheme: Boolean = true,
    val isDarkTheme: Boolean = false
)
