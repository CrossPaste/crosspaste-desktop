package com.crosspaste.ui.extension.sourceexclusion

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.crosspaste.ui.theme.AppUISize.xLarge

@Composable
fun SourceExclusionScreen() {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(start = xLarge, end = xLarge, bottom = xLarge),
        contentAlignment = Alignment.Center,
    ) {
        SourceExclusionContentView()
    }
}
