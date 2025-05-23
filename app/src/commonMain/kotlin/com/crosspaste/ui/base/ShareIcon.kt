package com.crosspaste.ui.base

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter

@Composable
expect fun facebook(): Painter

@Composable
expect fun linkedin(): Painter

@Composable
expect fun mail(): Painter

@Composable
expect fun reddit(): Painter

@Composable
expect fun telegram(): Painter

@Composable
expect fun weibo(): Painter

@Composable
expect fun x(): Painter
