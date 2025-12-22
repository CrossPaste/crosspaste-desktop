package com.crosspaste.listener

import androidx.compose.ui.window.WindowState
import java.awt.GraphicsDevice

interface ActiveGraphicsDevice {

    fun getGraphicsDevice(): GraphicsDevice

    fun getMainWindowState(): WindowState

    fun getSearchWindowState(init: Boolean): WindowState
}
