package com.crosspaste.ui.base

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import com.crosspaste.app.generated.resources.Res
import com.crosspaste.app.generated.resources.angles_up_down
import com.crosspaste.app.generated.resources.bolt
import com.crosspaste.app.generated.resources.clock
import com.crosspaste.app.generated.resources.font
import com.crosspaste.app.generated.resources.hashtag
import com.crosspaste.app.generated.resources.language
import com.crosspaste.app.generated.resources.network
import com.crosspaste.app.generated.resources.palette
import com.crosspaste.app.generated.resources.shield
import com.crosspaste.app.generated.resources.trash
import com.crosspaste.app.generated.resources.wifi
import org.jetbrains.compose.resources.painterResource

@Composable
actual fun anglesUpDown(): Painter = painterResource(Res.drawable.angles_up_down)

@Composable
actual fun arrowDown(): Painter = expandMore()

@Composable
actual fun arrowLeft(): Painter = chevronLeft()

@Composable
actual fun arrowRight(): Painter = chevronRight()

@Composable
actual fun arrowUp(): Painter = expandLess()

@Composable
actual fun bolt(): Painter = painterResource(Res.drawable.bolt)

@Composable
actual fun clock(): Painter = painterResource(Res.drawable.clock)

@Composable
actual fun font(): Painter = painterResource(Res.drawable.font)

@Composable
actual fun hashtag(): Painter = painterResource(Res.drawable.hashtag)

@Composable
actual fun language(): Painter = painterResource(Res.drawable.language)

@Composable
actual fun network(): Painter = painterResource(Res.drawable.network)

@Composable
actual fun palette(): Painter = painterResource(Res.drawable.palette)

@Composable
actual fun shield(): Painter = painterResource(Res.drawable.shield)

@Composable
actual fun trash(): Painter = painterResource(Res.drawable.trash)

@Composable
actual fun wifi(): Painter = painterResource(Res.drawable.wifi)
