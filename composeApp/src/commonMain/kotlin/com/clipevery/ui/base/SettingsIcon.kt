package com.clipevery.ui.base

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter

@Composable
fun arrowLeft(): Painter {
    return chevronLeft()
}

@Composable
fun arrowRight(): Painter {
    return chevronRight()
}

@Composable
fun arrowUp(): Painter {
    return expandLess()
}

@Composable
fun arrowDown(): Painter {
    return expandMore()
}
