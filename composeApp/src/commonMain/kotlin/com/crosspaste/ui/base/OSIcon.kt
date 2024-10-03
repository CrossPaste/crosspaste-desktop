package com.crosspaste.ui.base

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter

@Composable
expect fun android(): Painter

@Composable
expect fun ipad(): Painter

@Composable
expect fun iphone(): Painter

@Composable
expect fun linux(): Painter

@Composable
expect fun macos(): Painter

@Composable
expect fun windows(): Painter
