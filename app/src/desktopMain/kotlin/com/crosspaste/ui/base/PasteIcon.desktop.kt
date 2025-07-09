package com.crosspaste.ui.base

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import com.crosspaste.app.generated.resources.Res
import com.crosspaste.app.generated.resources.color_type
import com.crosspaste.app.generated.resources.file_slash
import com.crosspaste.app.generated.resources.file_type
import com.crosspaste.app.generated.resources.folder
import com.crosspaste.app.generated.resources.html_type
import com.crosspaste.app.generated.resources.image_slash
import com.crosspaste.app.generated.resources.image_type
import com.crosspaste.app.generated.resources.link_type
import com.crosspaste.app.generated.resources.rtf_type
import com.crosspaste.app.generated.resources.text_type
import org.jetbrains.compose.resources.painterResource

@Composable
actual fun color(): Painter {
    return painterResource(Res.drawable.color_type)
}

@Composable
actual fun file(): Painter {
    return painterResource(Res.drawable.file_type)
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
    return painterResource(Res.drawable.html_type)
}

@Composable
actual fun image(): Painter {
    return painterResource(Res.drawable.image_type)
}

@Composable
actual fun imageSlash(): Painter {
    return painterResource(Res.drawable.image_slash)
}

@Composable
actual fun link(): Painter {
    return painterResource(Res.drawable.link_type)
}

@Composable
actual fun rtf(): Painter {
    return painterResource(Res.drawable.rtf_type)
}

@Composable
actual fun text(): Painter {
    return painterResource(Res.drawable.text_type)
}
