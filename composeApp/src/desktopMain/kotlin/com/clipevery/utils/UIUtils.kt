package com.clipevery.utils

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.clipevery.model.AppUI
import java.awt.Dimension
import java.awt.Toolkit

fun initAppUI(): AppUI {
    return AppUI(
        width = 440.dp,
        height = 720.dp
    )
}
 fun getPreferredWindowSize(appUI: AppUI): DpSize {
    val screenSize: Dimension = Toolkit.getDefaultToolkit().screenSize
    val preferredWidth: Dp = (screenSize.width.dp * 0.8f)
    val preferredHeight: Dp = (screenSize.height.dp * 0.8f)
    val width: Dp = if (appUI.width < preferredWidth) appUI.width else preferredWidth
    val height: Dp = if (appUI.height < preferredHeight) appUI.height else preferredHeight
    return DpSize(width, height)
}