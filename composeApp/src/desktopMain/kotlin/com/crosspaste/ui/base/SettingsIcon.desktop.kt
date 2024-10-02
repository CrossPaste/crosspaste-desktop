package com.crosspaste.ui.base

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import com.crosspaste.composeapp.generated.resources.Res
import com.crosspaste.composeapp.generated.resources.angles_up_down
import com.crosspaste.composeapp.generated.resources.bolt
import com.crosspaste.composeapp.generated.resources.clock
import com.crosspaste.composeapp.generated.resources.hashtag
import com.crosspaste.composeapp.generated.resources.language
import com.crosspaste.composeapp.generated.resources.network
import com.crosspaste.composeapp.generated.resources.palette
import com.crosspaste.composeapp.generated.resources.rocket
import com.crosspaste.composeapp.generated.resources.shield
import com.crosspaste.composeapp.generated.resources.trash
import com.crosspaste.composeapp.generated.resources.wifi
import org.jetbrains.compose.resources.painterResource

@Composable
actual fun anglesUpDown(): Painter {
    return painterResource(Res.drawable.angles_up_down)
}

@Composable
actual fun arrowDown(): Painter {
    return expandMore()
}

@Composable
actual fun arrowLeft(): Painter {
    return chevronLeft()
}

@Composable
actual fun arrowRight(): Painter {
    return chevronRight()
}

@Composable
actual fun arrowUp(): Painter {
    return expandLess()
}

@Composable
actual fun bolt(): Painter {
    return painterResource(Res.drawable.bolt)
}

@Composable
actual fun clock(): Painter {
    return painterResource(Res.drawable.clock)
}

@Composable
actual fun hashtag(): Painter {
    return painterResource(Res.drawable.hashtag)
}

@Composable
actual fun language(): Painter {
    return painterResource(Res.drawable.language)
}

@Composable
actual fun network(): Painter {
    return painterResource(Res.drawable.network)
}

@Composable
actual fun palette(): Painter {
    return painterResource(Res.drawable.palette)
}

@Composable
actual fun rocket(): Painter {
    return painterResource(Res.drawable.rocket)
}

@Composable
actual fun shield(): Painter {
    return painterResource(Res.drawable.shield)
}

@Composable
actual fun trash(): Painter {
    return painterResource(Res.drawable.trash)
}

@Composable
actual fun wifi(): Painter {
    return painterResource(Res.drawable.wifi)
}
