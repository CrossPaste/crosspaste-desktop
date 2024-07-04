package com.crosspaste.ui.base

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource

@Composable
fun text(): Painter {
    return painterResource("icon/paste/text.svg")
}

@Composable
fun link(): Painter {
    return painterResource("icon/paste/link.svg")
}

@Composable
fun html(): Painter {
    return painterResource("icon/paste/html.svg")
}

@Composable
fun image(): Painter {
    return painterResource("icon/paste/image.svg")
}

@Composable
fun file(): Painter {
    return painterResource("icon/paste/file.svg")
}

@Composable
fun folder(): Painter {
    return painterResource("icon/paste/folder.svg")
}

@Composable
fun fileSlash(): Painter {
    return painterResource("icon/paste/file-slash.svg")
}

@Composable
fun imageSlash(): Painter {
    return painterResource("icon/paste/image-slash.svg")
}
