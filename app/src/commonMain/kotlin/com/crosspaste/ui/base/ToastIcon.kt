package com.crosspaste.ui.base

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter

@Composable
expect fun error(): Painter

@Composable
expect fun info(): Painter

@Composable
expect fun success(): Painter

@Composable
expect fun warning(): Painter
