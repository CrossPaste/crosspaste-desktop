package com.crosspaste.ui.base

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource

@Composable
actual fun anglesUpDown(): Painter {
    return painterResource("icon/base/angles_up_down.svg")
}

@Composable
actual fun arrowDown(): Painter {
    return expandMore()
}

@Composable
actual fun arrowLeft(): Painter {
    return chevronLeft()
}

@Composable
actual fun arrowRight(): Painter {
    return chevronRight()
}

@Composable
actual fun arrowUp(): Painter {
    return expandLess()
}

@Composable
actual fun bolt(): Painter {
    return painterResource("icon/base/bolt.svg")
}

@Composable
actual fun clock(): Painter {
    return painterResource("icon/base/clock.svg")
}

@Composable
actual fun hashtag(): Painter {
    return painterResource("icon/base/hashtag.svg")
}

@Composable
actual fun language(): Painter {
    return painterResource("icon/base/language.svg")
}

@Composable
actual fun network(): Painter {
    return painterResource("icon/base/network.svg")
}

@Composable
actual fun palette(): Painter {
    return painterResource("icon/base/palette.svg")
}

@Composable
actual fun rocket(): Painter {
    return painterResource("icon/base/rocket.svg")
}

@Composable
actual fun shield(): Painter {
    return painterResource("icon/base/shield.svg")
}

@Composable
actual fun trash(): Painter {
    return painterResource("icon/base/trash.svg")
}

@Composable
actual fun wifi(): Painter {
    return painterResource("icon/base/wifi.svg")
}
