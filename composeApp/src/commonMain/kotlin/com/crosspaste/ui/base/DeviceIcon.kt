package com.crosspaste.ui.base

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter

@Composable
expect fun block(): Painter

@Composable
expect fun allowReceive(): Painter

@Composable
expect fun allowSend(): Painter

@Composable
expect fun sync(): Painter

@Composable
expect fun unverified(): Painter
