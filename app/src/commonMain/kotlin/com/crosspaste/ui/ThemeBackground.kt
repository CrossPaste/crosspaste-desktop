package com.crosspaste.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.crosspaste.ui.theme.AppUIColors

@Composable
fun ThemeBackground() {
    Canvas(
        modifier =
            Modifier
                .fillMaxSize()
                .background(AppUIColors.appBackground),
    ) {
    }
}
