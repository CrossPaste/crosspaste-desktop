package com.crosspaste.ui.base

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import com.crosspaste.app.generated.resources.Res
import com.crosspaste.app.generated.resources.hashtag
import org.jetbrains.compose.resources.painterResource

@Composable
actual fun hashtag(): Painter = painterResource(Res.drawable.hashtag)
