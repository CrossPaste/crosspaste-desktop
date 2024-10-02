package com.crosspaste.ui.base

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import com.crosspaste.composeapp.generated.resources.Res
import com.crosspaste.composeapp.generated.resources.error
import com.crosspaste.composeapp.generated.resources.info
import com.crosspaste.composeapp.generated.resources.success
import com.crosspaste.composeapp.generated.resources.warning
import org.jetbrains.compose.resources.painterResource

@Composable
actual fun error(): Painter {
    return painterResource(Res.drawable.error)
}

@Composable
actual fun info(): Painter {
    return painterResource(Res.drawable.info)
}

@Composable
actual fun success(): Painter {
    return painterResource(Res.drawable.success)
}

@Composable
actual fun warning(): Painter {
    return painterResource(Res.drawable.warning)
}
