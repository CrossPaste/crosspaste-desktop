package com.crosspaste.ui.base

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import com.crosspaste.app.generated.resources.Res
import com.crosspaste.app.generated.resources.color
import com.crosspaste.app.generated.resources.file
import com.crosspaste.app.generated.resources.file_slash
import com.crosspaste.app.generated.resources.folder
import com.crosspaste.app.generated.resources.html
import com.crosspaste.app.generated.resources.image
import com.crosspaste.app.generated.resources.image_slash
import com.crosspaste.app.generated.resources.link
import com.crosspaste.app.generated.resources.rtf
import com.crosspaste.app.generated.resources.text
import org.jetbrains.compose.resources.painterResource

@Composable
actual fun color(): Painter {
    return painterResource(Res.drawable.color)
}

@Composable
actual fun file(): Painter {
    return painterResource(Res.drawable.file)
}

@Composable
actual fun fileSlash(): Painter {
    return painterResource(Res.drawable.file_slash)
}

@Composable
actual fun folder(): Painter {
    return painterResource(Res.drawable.folder)
}

@Composable
actual fun html(): Painter {
    return painterResource(Res.drawable.html)
}

@Composable
actual fun image(): Painter {
    return painterResource(Res.drawable.image)
}

@Composable
actual fun imageSlash(): Painter {
    return painterResource(Res.drawable.image_slash)
}

@Composable
actual fun link(): Painter {
    return painterResource(Res.drawable.link)
}

@Composable
actual fun rtf(): Painter {
    return painterResource(Res.drawable.rtf)
}

@Composable
actual fun text(): Painter {
    return painterResource(Res.drawable.text)
}
