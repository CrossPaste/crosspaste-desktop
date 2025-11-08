package com.crosspaste.ui.extension.ocr

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.crosspaste.ui.WindowDecoration
import com.crosspaste.ui.theme.AppUISize.medium

@Composable
fun OCRScreen() {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = medium)
                .padding(bottom = medium),
    ) {
        WindowDecoration()
        OCRContentView()
    }
}
