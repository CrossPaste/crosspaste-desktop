package com.crosspaste.share

import androidx.compose.runtime.Composable

interface AppSharePlatform {
    val platformName: String

    @Composable
    fun ButtonPlatform()

    suspend fun action(appShareService: AppShareService)
}
