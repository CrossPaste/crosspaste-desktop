package com.crosspaste.ui.base

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter

@Composable
expect fun chevronLeft(): Painter

@Composable
expect fun chevronRight(): Painter

@Composable
expect fun expandLess(): Painter

@Composable
expect fun expandMore(): Painter
