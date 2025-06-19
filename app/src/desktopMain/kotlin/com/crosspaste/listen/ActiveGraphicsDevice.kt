package com.crosspaste.listen

import androidx.compose.ui.window.WindowState
import java.awt.GraphicsDevice

interface ActiveGraphicsDevice {

    fun getGraphicsDevice(): GraphicsDevice

    fun getSearchWindowState(graphicsDevice: GraphicsDevice): WindowState
}
