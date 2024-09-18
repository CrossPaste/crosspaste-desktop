package com.crosspaste.ui.base

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource

@Composable
actual fun file(): Painter {
    return painterResource("icon/paste/file.svg")
}

@Composable
actual fun fileSlash(): Painter {
    return painterResource("icon/paste/file-slash.svg")
}

@Composable
actual fun folder(): Painter {
    return painterResource("icon/paste/folder.svg")
}

@Composable
actual fun html(): Painter {
    return painterResource("icon/paste/html.svg")
}

@Composable
actual fun image(): Painter {
    return painterResource("icon/paste/image.svg")
}

@Composable
actual fun imageSlash(): Painter {
    return painterResource("icon/paste/image-slash.svg")
}

@Composable
actual fun link(): Painter {
    return painterResource("icon/paste/link.svg")
}

@Composable
actual fun text(): Painter {
    return painterResource("icon/paste/text.svg")
}
