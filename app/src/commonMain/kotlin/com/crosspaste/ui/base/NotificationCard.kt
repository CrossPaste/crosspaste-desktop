package com.crosspaste.ui.base

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
expect fun NotificationCard(
    containerColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
)
