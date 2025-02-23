package com.crosspaste.ui.base

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import com.crosspaste.app.generated.resources.Res
import com.crosspaste.app.generated.resources.android
import com.crosspaste.app.generated.resources.ipad
import com.crosspaste.app.generated.resources.iphone
import com.crosspaste.app.generated.resources.linux
import com.crosspaste.app.generated.resources.macos
import com.crosspaste.app.generated.resources.windows
import org.jetbrains.compose.resources.painterResource

@Composable
actual fun android(): Painter {
    return painterResource(Res.drawable.android)
}

@Composable
actual fun ipad(): Painter {
    return painterResource(Res.drawable.ipad)
}

@Composable
actual fun iphone(): Painter {
    return painterResource(Res.drawable.iphone)
}

@Composable
actual fun linux(): Painter {
    return painterResource(Res.drawable.linux)
}

@Composable
actual fun macos(): Painter {
    return painterResource(Res.drawable.macos)
}

@Composable
actual fun windows(): Painter {
    return painterResource(Res.drawable.windows)
}
