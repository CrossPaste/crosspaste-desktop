package com.crosspaste.ui.base

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter

@Composable
expect fun question(): Painter

@Composable
expect fun refresh(): Painter

@Composable
expect fun save(): Painter
