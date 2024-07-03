package com.crosspaste.ui.base

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource

@Composable
fun macos(): Painter {
    return painterResource("icon/device/macos.svg")
}

@Composable
fun windows(): Painter {
    return painterResource("icon/device/windows.svg")
}

@Composable
fun linux(): Painter {
    return painterResource("icon/device/linux.svg")
}

@Composable
fun iphone(): Painter {
    return painterResource("icon/device/iphone.svg")
}

@Composable
fun ipad(): Painter {
    return painterResource("icon/device/ipad.svg")
}

@Composable
fun android(): Painter {
    return painterResource("icon/device/android.svg")
}
