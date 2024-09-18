package com.crosspaste.ui.base

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource

@Composable
actual fun chevronLeft(): Painter {
    return painterResource("icon/base/chevron_left.svg")
}

@Composable
actual fun chevronRight(): Painter {
    return painterResource("icon/base/chevron_right.svg")
}

@Composable
actual fun expandLess(): Painter {
    return painterResource("icon/base/expand_less.svg")
}

@Composable
actual fun expandMore(): Painter {
    return painterResource("icon/base/expand_more.svg")
}
