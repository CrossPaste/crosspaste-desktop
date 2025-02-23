package com.crosspaste.ui.base

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import com.crosspaste.app.generated.resources.Res
import com.crosspaste.app.generated.resources.crosspaste_icon
import org.jetbrains.compose.resources.painterResource

@Composable
actual fun crosspasteIcon(): Painter {
    return painterResource(Res.drawable.crosspaste_icon)
}
