package com.crosspaste.ui.base

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
fun wifi(): Painter {
    return painterResource("icon/base/wifi.svg")
}

@Composable
fun language(): Painter {
    return painterResource("icon/base/language.svg")
}

@Composable
fun palette(): Painter {
    return painterResource("icon/base/palette.svg")
}

@Composable
fun shield(): Painter {
    return painterResource("icon/base/shield.svg")
}

@Composable
fun bolt(): Painter {
    return painterResource("icon/base/bolt.svg")
}

@Composable
fun rocket(): Painter {
    return painterResource("icon/base/rocket.svg")
}
