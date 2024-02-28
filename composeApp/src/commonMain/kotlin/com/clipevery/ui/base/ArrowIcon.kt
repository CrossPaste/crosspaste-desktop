package com.clipevery.ui.base

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource

@Composable
fun chevronLeft(): Painter {
    return painterResource("icon/base/chevron_left.svg")
}

@Composable
fun chevronRight(): Painter {
    return painterResource("icon/base/chevron_right.svg")
}

@Composable
fun expandMore(): Painter {
    return painterResource("icon/base/expand_more.svg")
}

@Composable
fun expandLess(): Painter {
    return painterResource("icon/base/expand_less.svg")
}