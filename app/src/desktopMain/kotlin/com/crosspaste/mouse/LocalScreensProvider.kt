package com.crosspaste.mouse

import java.awt.GraphicsEnvironment

/**
 * Source of truth for "what monitors does this machine have right now."
 *
 * The mouse daemon emits [IpcEvent.Initialized] with the same information,
 * but only after it has been spawned. Until then — and across the brief
 * window before the [com.crosspaste.ui.mouse.ScreenArrangementViewModel]
 * starts collecting — we still want the canvas to render the local
 * screens so the user has a visual baseline.
 *
 * Implementations must be safe to call on any thread; the default AWT
 * implementation just queries the JVM graphics environment.
 */
fun interface LocalScreensProvider {
    fun snapshot(): List<ScreenInfo>
}

class AwtLocalScreensProvider : LocalScreensProvider {
    override fun snapshot(): List<ScreenInfo> {
        val env =
            runCatching { GraphicsEnvironment.getLocalGraphicsEnvironment() }.getOrNull()
                ?: return emptyList()
        if (env.isHeadlessInstance) return emptyList()
        val primary = env.defaultScreenDevice
        return env.screenDevices.mapIndexed { index, device ->
            val config = device.defaultConfiguration
            val bounds = config.bounds
            ScreenInfo(
                id = index,
                width = bounds.width,
                height = bounds.height,
                x = bounds.x,
                y = bounds.y,
                scaleFactor = config.defaultTransform.scaleX.takeIf { it > 0.0 } ?: 1.0,
                isPrimary = device == primary,
            )
        }
    }
}
