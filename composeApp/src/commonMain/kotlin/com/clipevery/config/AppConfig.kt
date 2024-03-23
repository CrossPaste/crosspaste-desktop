package com.clipevery.config

import com.clipevery.app.AppEnv
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.util.Locale
import java.util.UUID

@Serializable
data class AppConfig(
    @Transient val appEnv: AppEnv = AppEnv.PRODUCTION,
    val appInstanceId: String = UUID.randomUUID().toString(),
    val language: String = Locale.getDefault().language,
    val isFollowSystemTheme: Boolean = true,
    val isDarkTheme: Boolean = false,
    val port: Int = 13129,
    val isEncryptSync: Boolean = true
) {

    constructor(other: AppConfig, appEnv: AppEnv) : this(
        appEnv = appEnv,
        appInstanceId = other.appInstanceId,
        language = other.language,
        isFollowSystemTheme = other.isFollowSystemTheme,
        isDarkTheme = other.isDarkTheme,
        port = other.port,
        isEncryptSync = other.isEncryptSync
    )
}
