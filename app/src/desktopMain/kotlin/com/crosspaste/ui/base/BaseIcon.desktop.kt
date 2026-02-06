package com.crosspaste.ui.base

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import com.crosspaste.app.generated.resources.Res
import com.crosspaste.app.generated.resources.question
import com.crosspaste.app.generated.resources.refresh
import com.crosspaste.app.generated.resources.save
import org.jetbrains.compose.resources.painterResource

@Composable
actual fun question(): Painter = painterResource(Res.drawable.question)

@Composable
actual fun refresh(): Painter = painterResource(Res.drawable.refresh)

@Composable
actual fun save(): Painter = painterResource(Res.drawable.save)
