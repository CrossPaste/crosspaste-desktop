package com.crosspaste.ui.base

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter

@Composable
expect fun file(): Painter

@Composable
expect fun fileSlash(): Painter

@Composable
expect fun folder(): Painter

@Composable
expect fun htmlOrRtf(): Painter

@Composable
expect fun image(): Painter

@Composable
expect fun imageSlash(): Painter

@Composable
expect fun link(): Painter

@Composable
expect fun text(): Painter
