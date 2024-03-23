package com.clipevery.ui.base

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource

@Composable
fun feed(): Painter {
    return painterResource("icon/clip/feed.svg")
}

@Composable
fun link(): Painter {
    return painterResource("icon/clip/link.svg")
}

@Composable
fun html(): Painter {
    return painterResource("icon/clip/html.svg")
}

@Composable
fun image(): Painter {
    return painterResource("icon/clip/image.svg")
}

@Composable
fun file(): Painter {
    return painterResource("icon/clip/file.svg")
}

@Composable
fun folder(): Painter {
    return painterResource("icon/clip/folder.svg")
}

@Composable
fun fileSlash(): Painter {
    return painterResource("icon/clip/file-slash.svg")
}

@Composable
fun imageSlash(): Painter {
    return painterResource("icon/clip/image-slash.svg")
}