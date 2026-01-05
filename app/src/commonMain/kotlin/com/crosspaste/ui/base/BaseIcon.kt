package com.crosspaste.ui.base

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter

@Composable
expect fun ascSort(): Painter

@Composable
expect fun descSort(): Painter

@Composable
expect fun favorite(): Painter

@Composable
expect fun noFavorite(): Painter

@Composable
expect fun question(): Painter

@Composable
expect fun refresh(): Painter

@Composable
expect fun save(): Painter

@Composable
expect fun settings(): Painter

@Composable
expect fun toTop(): Painter
