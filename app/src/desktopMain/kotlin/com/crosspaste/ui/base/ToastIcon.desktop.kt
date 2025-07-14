package com.crosspaste.ui.base

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import com.crosspaste.app.generated.resources.Res
import com.crosspaste.app.generated.resources.error
import com.crosspaste.app.generated.resources.info
import com.crosspaste.app.generated.resources.success
import com.crosspaste.app.generated.resources.warning
import org.jetbrains.compose.resources.painterResource

@Composable
actual fun error(): Painter = painterResource(Res.drawable.error)

@Composable
actual fun info(): Painter = painterResource(Res.drawable.info)

@Composable
actual fun success(): Painter = painterResource(Res.drawable.success)

@Composable
actual fun warning(): Painter = painterResource(Res.drawable.warning)
