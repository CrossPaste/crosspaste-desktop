package com.crosspaste.ui.base

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource

@Composable
actual fun block(): Painter {
    return painterResource("icon/device/block.svg")
}

@Composable
actual fun allowReceive(): Painter {
    return painterResource("icon/device/allow-receive.svg")
}

@Composable
actual fun allowSend(): Painter {
    return painterResource("icon/device/allow-send.svg")
}

@Composable
actual fun sync(): Painter {
    return painterResource("icon/device/sync.svg")
}

@Composable
actual fun unverified(): Painter {
    return painterResource("icon/device/unverified.svg")
}
