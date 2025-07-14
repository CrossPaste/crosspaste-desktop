package com.crosspaste.ui.base

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import com.crosspaste.app.generated.resources.Res
import com.crosspaste.app.generated.resources.allow_receive
import com.crosspaste.app.generated.resources.allow_send
import com.crosspaste.app.generated.resources.block
import com.crosspaste.app.generated.resources.devices
import com.crosspaste.app.generated.resources.sync
import com.crosspaste.app.generated.resources.unverified
import org.jetbrains.compose.resources.painterResource

@Composable
actual fun allowReceive(): Painter = painterResource(Res.drawable.allow_receive)

@Composable
actual fun allowSend(): Painter = painterResource(Res.drawable.allow_send)

@Composable
actual fun block(): Painter = painterResource(Res.drawable.block)

@Composable
actual fun devices(): Painter = painterResource(Res.drawable.devices)

@Composable
actual fun sync(): Painter = painterResource(Res.drawable.sync)

@Composable
actual fun unverified(): Painter = painterResource(Res.drawable.unverified)
