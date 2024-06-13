package com.clipevery.utils

import java.awt.GraphicsDevice

fun GraphicsDevice.contains(point: java.awt.Point): Boolean {
    val bounds = this.defaultConfiguration.bounds
    val width: Int = this.displayMode.width
    val height: Int = this.displayMode.height
    return point.x >= bounds.x && point.x <= bounds.x + width &&
        point.y >= bounds.y && point.y <= bounds.y + height
}
