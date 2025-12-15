package com.crosspaste.platform.linux

import org.jetbrains.jewel.intui.window.DecoratedWindowIconKeys
import org.jetbrains.jewel.ui.icon.PathIconKey
import java.lang.reflect.Field

fun patchDecoratedWindowIcons() {
    try {
        val targetInstance = DecoratedWindowIconKeys
        val targetClass = DecoratedWindowIconKeys::class.java

        val patches =
            mapOf(
                "minimize" to "window/minimize.svg",
                "maximize" to "window/maximize.svg",
                "restore" to "window/restore.svg",
                "close" to "window/close.svg",
            )

        patches.forEach { (fieldName, newPath) ->
            val field: Field = targetClass.getDeclaredField(fieldName)
            field.isAccessible = true

            val newIconKey = PathIconKey(newPath, targetClass)

            field.set(targetInstance, newIconKey)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
