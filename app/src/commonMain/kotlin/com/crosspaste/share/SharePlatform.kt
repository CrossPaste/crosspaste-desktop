package com.crosspaste.share

import androidx.compose.runtime.Composable

interface SharePlatform {
    val platformName: String

    @Composable
    fun ButtonPlatform()

    suspend fun action(shareService: ShareService)
}
