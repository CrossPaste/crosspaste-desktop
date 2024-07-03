package com.crosspaste.ui.base

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource

@Composable
fun sync(): Painter {
    return painterResource("icon/device/sync.svg")
}

@Composable
fun allowSend(): Painter {
    return painterResource("icon/device/allow-send.svg")
}

@Composable
fun allowReceive(): Painter {
    return painterResource("icon/device/allow-receive.svg")
}

@Composable
fun unverified(): Painter {
    return painterResource("icon/device/unverified.svg")
}

@Composable
fun block(): Painter {
    return painterResource("icon/device/block.svg")
}
