package com.clipevery.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import com.clipevery.platform.currentPlatform
import java.awt.event.MouseAdapter


fun getTrayMouseAdapter(windowState: WindowState, mouseClickedAction: () -> Unit): MouseAdapter {
    return if(currentPlatform().isMacos()) {
        MacTrayMouseClicked(windowState, mouseClickedAction)
    } else {
        WindowsTrayMouseClicked()
    }
}

class MacTrayMouseClicked(private val windowState: WindowState, private val mouseClickedAction: () -> Unit): MouseAdapter() {

    override fun mouseClicked(e: java.awt.event.MouseEvent) {
        mouseClickedAction()
        windowState.position = WindowPosition.Absolute(
            x = calculatePosition(e.x.dp, windowState.size.width),
            y = 32.dp
        )
    }

    private fun calculatePosition(x: Dp, width: Dp): Dp {
        val fNum = x / 32.dp
        val iNum = fNum.toInt()
        return if (fNum - iNum < 0.5f) {
            iNum * 32.dp - (width / 2)
        } else {
            (iNum + 1) * 32.dp - (width / 2)
        }
    }
}

class WindowsTrayMouseClicked: MouseAdapter() {

    override fun mouseClicked(e: java.awt.event.MouseEvent) {

    }
}