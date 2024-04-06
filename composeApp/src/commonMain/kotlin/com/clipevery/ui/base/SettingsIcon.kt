package com.clipevery.ui.base

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource

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


@Composable
fun hashtag(): Painter {
    return painterResource("icon/base/hashtag.svg")
}

@Composable
fun clock(): Painter {
    return painterResource("icon/base/clock.svg")
}

@Composable
fun trash(): Painter {
    return painterResource("icon/base/trash.svg")
}

@Composable
fun anglesUpDown(): Painter {
    return painterResource("icon/base/angles_up_down.svg")
}

@Composable
fun network(): Painter {
    return painterResource("icon/base/network.svg")
}

@Composable
fun towerBroadcast(): Painter {
    return painterResource("icon/base/tower_broadcast.svg")
}