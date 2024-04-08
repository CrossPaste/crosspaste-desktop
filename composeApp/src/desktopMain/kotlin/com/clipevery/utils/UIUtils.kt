package com.clipevery.utils

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.clipevery.app.AppUI
import java.awt.GraphicsEnvironment
import java.awt.Toolkit

fun getPreferredWindowSize(appUI: AppUI): DpSize {
    val gd = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice
    val bounds = gd.defaultConfiguration.bounds
    val insets = Toolkit.getDefaultToolkit().getScreenInsets(gd.defaultConfiguration)

    val usableWidth = bounds.width - insets.right
    val usableHeight = bounds.height - insets.bottom

    val preferredWidth: Dp = (usableWidth.dp * 0.8f)
    val preferredHeight: Dp = (usableHeight.dp * 0.8f)
    val width: Dp = if (appUI.width < preferredWidth) appUI.width else preferredWidth
    val height: Dp = if (appUI.height < preferredHeight) appUI.height else preferredHeight
    return DpSize(width, height)
}
