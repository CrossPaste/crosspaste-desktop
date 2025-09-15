package com.crosspaste.ui

import androidx.compose.runtime.Composable

interface ScreenProvider : NavigationManager {

    @Composable
    fun screen()
}
