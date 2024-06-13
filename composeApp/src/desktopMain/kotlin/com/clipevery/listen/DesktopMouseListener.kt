package com.clipevery.listen

import com.clipevery.utils.contains
import com.github.kwhat.jnativehook.mouse.NativeMouseEvent
import com.github.kwhat.jnativehook.mouse.NativeMouseListener
import java.awt.GraphicsDevice
import java.awt.GraphicsEnvironment
import java.awt.Point

object DesktopMouseListener : NativeMouseListener, ActiveGraphicsDevice {

    private var point: Point? = null

    override fun nativeMousePressed(nativeEvent: NativeMouseEvent) {
        point = nativeEvent.point
    }

    override fun getGraphicsDevice(): GraphicsDevice? {
        val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
        val scDevices = ge.screenDevices

        return point?.let {
            scDevices.firstOrNull { device ->
                device.contains(it)
            }
        }
    }
}
