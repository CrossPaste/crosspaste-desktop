package com.crosspaste.ui.base

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import com.crosspaste.app.generated.resources.Res
import com.crosspaste.app.generated.resources.facebook
import com.crosspaste.app.generated.resources.linkedin
import com.crosspaste.app.generated.resources.mail
import com.crosspaste.app.generated.resources.reddit
import com.crosspaste.app.generated.resources.telegram
import com.crosspaste.app.generated.resources.weibo
import com.crosspaste.app.generated.resources.x
import org.jetbrains.compose.resources.painterResource

@Composable
actual fun facebook(): Painter = painterResource(Res.drawable.facebook)

@Composable
actual fun linkedin(): Painter = painterResource(Res.drawable.linkedin)

@Composable
actual fun mail(): Painter = painterResource(Res.drawable.mail)

@Composable
actual fun reddit(): Painter = painterResource(Res.drawable.reddit)

@Composable
actual fun telegram(): Painter = painterResource(Res.drawable.telegram)

@Composable
actual fun weibo(): Painter = painterResource(Res.drawable.weibo)

@Composable
actual fun x(): Painter = painterResource(Res.drawable.x)
