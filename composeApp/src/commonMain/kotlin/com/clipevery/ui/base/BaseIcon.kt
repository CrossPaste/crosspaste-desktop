package com.clipevery.ui.base

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource

@Composable
fun question(): Painter {
    return painterResource("icon/base/question.svg")
}

@Composable
fun autoRenew(): Painter {
    return painterResource("icon/base/autorenew.svg")
}

@Composable
fun arrowBack(): Painter {
    return painterResource("icon/base/arrow_back.svg")
}

@Composable
fun magnifying(): Painter {
    return painterResource("icon/base/magnifying.svg")
}

@Composable
fun scan(): Painter {
    return painterResource("icon/base/scan.svg")
}

@Composable
fun add(): Painter {
    return painterResource("icon/base/add.svg")
}

@Composable
fun warning(): Painter {
    return painterResource("icon/base/warning.svg")
}

@Composable
fun remove(): Painter {
    return painterResource("icon/base/remove.svg")
}

@Composable
fun starRegular(): Painter {
    return painterResource("icon/base/star-regular.svg")
}

@Composable
fun starSolid(): Painter {
    return painterResource("icon/base/star-solid.svg")
}

@Composable
fun database(): Painter {
    return painterResource("icon/base/database.svg")
}

@Composable
fun percent(): Painter {
    return painterResource("icon/base/percent.svg")
}
