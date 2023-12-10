package com.clipevery.config

import kotlinx.serialization.Serializable
import java.util.Locale
import java.util.UUID

@Serializable
data class AppConfig(
    val appInstanceId: String = UUID.randomUUID().toString(),
    val bindingState: Boolean = false,
    val language: String = Locale.getDefault().language,
    val isFollowSystem: Boolean = true,
    val isDark: Boolean = false
)
