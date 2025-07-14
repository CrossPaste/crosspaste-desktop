package com.crosspaste.utils

import java.awt.GraphicsDevice
import java.awt.Point

// In Linux, the coordinates of the upper left corner of the main screen may not be (0, 0)
// so we need to subtract the main screen coordinates to calculate the relative position
fun GraphicsDevice.contains(
    point: Point,
    defaultX: Int = 0,
    defaultY: Int = 0,
): Boolean {
    val bounds = this.defaultConfiguration.bounds
    val width: Int = this.displayMode.width
    val height: Int = this.displayMode.height

    val topX = bounds.x - defaultX
    val topY = bounds.y - defaultY

    return point.x >= topX &&
        point.x <= topX + width &&
        point.y >= topY &&
        point.y <= topY + height
}
