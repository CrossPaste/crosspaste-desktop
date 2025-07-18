package com.crosspaste.ui.base

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import com.crosspaste.app.generated.resources.Res
import com.crosspaste.app.generated.resources.accessibility
import com.crosspaste.app.generated.resources.layout
import com.crosspaste.app.generated.resources.pushpin_active
import com.crosspaste.app.generated.resources.pushpin_inactive
import com.crosspaste.app.generated.resources.restart
import org.jetbrains.compose.resources.painterResource

@Composable
fun accessibility(): Painter = painterResource(Res.drawable.accessibility)

@Composable
fun layout(): Painter = painterResource(Res.drawable.layout)

@Composable
fun pushpinActive(): Painter = painterResource(Res.drawable.pushpin_active)

@Composable
fun pushpinInactive(): Painter = painterResource(Res.drawable.pushpin_inactive)

@Composable
fun restart(): Painter = painterResource(Res.drawable.restart)
