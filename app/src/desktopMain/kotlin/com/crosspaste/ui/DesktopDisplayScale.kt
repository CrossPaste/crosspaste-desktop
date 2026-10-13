package com.crosspaste.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import com.crosspaste.platform.linux.api.X11Api
import java.awt.GraphicsEnvironment
import java.awt.Toolkit

/**
 * Plasma publishes its XWayland scale through Xft.dpi. JBR's XToolkit instead prefers
 * GNOME's GSettings and only exposes integer scales. Keep window positions in AWT units,
 * compensate window dimensions at their boundary, and use the fractional density in content.
 */
object DesktopDisplayScale {
    val correction: Float by lazy {
        val desktop = System.getenv("XDG_CURRENT_DESKTOP").orEmpty().split(':')
        if (System.getProperty("os.name") != "Linux" ||
            desktop.none { it.equals("KDE", ignoreCase = true) } ||
            GraphicsEnvironment.isHeadless() ||
            Toolkit.getDefaultToolkit().javaClass.name != "sun.awt.X11.XToolkit" ||
            System.getProperty("sun.java2d.uiScale") != null ||
            System.getProperty("sun.java2d.uiScale.enabled") == "false"
        ) {
            1f
        } else {
            val x11 = X11Api.INSTANCE
            val display = x11.XOpenDisplay(null)
            if (display == null) {
                1f
            } else {
                try {
                    val resources = x11.XResourceManagerString(display)?.getString(0).orEmpty()
                    val dpi = parseDpi(resources)
                    val awtScale =
                        GraphicsEnvironment
                            .getLocalGraphicsEnvironment()
                            .defaultScreenDevice.defaultConfiguration.defaultTransform.scaleX
                            .toFloat()
                    correctionFor(dpi, awtScale)
                } finally {
                    x11.XCloseDisplay(display)
                }
            }
        }
    }

    internal fun parseDpi(resources: String): Float? =
        resources
            .lineSequence()
            .map { it.split(':', limit = 2) }
            .firstOrNull { it.size == 2 && it[0].trim() == "Xft.dpi" }
            ?.get(1)
            ?.trim()
            ?.toFloatOrNull()
            ?.takeIf { it.isFinite() && it > 0f }

    internal fun correctionFor(
        dpi: Float?,
        awtScale: Float,
    ): Float = if (dpi == null) 1f else dpi / 96f / awtScale

    fun Dp.toWindowUnits(): Dp = this * correction

    fun DpSize.toWindowUnits(): DpSize = DpSize(width.toWindowUnits(), height.toWindowUnits())
}

/** Install inside each window: Compose replaces the density supplied outside a Window. */
@Composable
fun DisplayScaleContent(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density * DesktopDisplayScale.correction, density.fontScale),
        content = content,
    )
}
