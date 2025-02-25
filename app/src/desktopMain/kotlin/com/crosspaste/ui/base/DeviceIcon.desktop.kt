package com.crosspaste.ui.base

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import com.crosspaste.app.generated.resources.Res
import com.crosspaste.app.generated.resources.allow_receive
import com.crosspaste.app.generated.resources.allow_send
import com.crosspaste.app.generated.resources.block
import com.crosspaste.app.generated.resources.devices
import com.crosspaste.app.generated.resources.qr_code
import com.crosspaste.app.generated.resources.sync
import com.crosspaste.app.generated.resources.token
import com.crosspaste.app.generated.resources.unverified
import org.jetbrains.compose.resources.painterResource

@Composable
actual fun allowReceive(): Painter {
    return painterResource(Res.drawable.allow_receive)
}

@Composable
actual fun allowSend(): Painter {
    return painterResource(Res.drawable.allow_send)
}

@Composable
actual fun block(): Painter {
    return painterResource(Res.drawable.block)
}

@Composable
actual fun devices(): Painter {
    return painterResource(Res.drawable.devices)
}

@Composable
actual fun qrCode(): Painter {
    return painterResource(Res.drawable.qr_code)
}

@Composable
actual fun sync(): Painter {
    return painterResource(Res.drawable.sync)
}

@Composable
actual fun token(): Painter {
    return painterResource(Res.drawable.token)
}

@Composable
actual fun unverified(): Painter {
    return painterResource(Res.drawable.unverified)
}
