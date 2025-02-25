package com.crosspaste.ui.base

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import com.crosspaste.app.generated.resources.Res
import com.crosspaste.app.generated.resources.chevron_left
import com.crosspaste.app.generated.resources.chevron_right
import com.crosspaste.app.generated.resources.expand_less
import com.crosspaste.app.generated.resources.expand_more
import org.jetbrains.compose.resources.painterResource

@Composable
actual fun chevronLeft(): Painter {
    return painterResource(Res.drawable.chevron_left)
}

@Composable
actual fun chevronRight(): Painter {
    return painterResource(Res.drawable.chevron_right)
}

@Composable
actual fun expandLess(): Painter {
    return painterResource(Res.drawable.expand_less)
}

@Composable
actual fun expandMore(): Painter {
    return painterResource(Res.drawable.expand_more)
}
