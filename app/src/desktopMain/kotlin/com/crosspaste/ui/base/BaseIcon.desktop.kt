package com.crosspaste.ui.base

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import com.crosspaste.app.generated.resources.Res
import com.crosspaste.app.generated.resources.favorite
import com.crosspaste.app.generated.resources.no_favorite
import com.crosspaste.app.generated.resources.question
import com.crosspaste.app.generated.resources.refresh
import com.crosspaste.app.generated.resources.save
import com.crosspaste.app.generated.resources.settings
import com.crosspaste.app.generated.resources.sort_asc
import com.crosspaste.app.generated.resources.sort_desc
import com.crosspaste.app.generated.resources.to_top
import org.jetbrains.compose.resources.painterResource

@Composable
actual fun ascSort(): Painter = painterResource(Res.drawable.sort_asc)

@Composable
actual fun descSort(): Painter = painterResource(Res.drawable.sort_desc)

@Composable
actual fun favorite(): Painter = painterResource(Res.drawable.favorite)

@Composable
actual fun noFavorite(): Painter = painterResource(Res.drawable.no_favorite)

@Composable
actual fun question(): Painter = painterResource(Res.drawable.question)

@Composable
actual fun refresh(): Painter = painterResource(Res.drawable.refresh)

@Composable
actual fun save(): Painter = painterResource(Res.drawable.save)

@Composable
actual fun settings(): Painter = painterResource(Res.drawable.settings)

@Composable
actual fun toTop(): Painter = painterResource(Res.drawable.to_top)
