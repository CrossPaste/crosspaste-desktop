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