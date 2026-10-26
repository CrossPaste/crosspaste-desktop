package com.crosspaste.ui.base

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import java.awt.Toolkit

/**
 * Compose Desktop fixes the double-tap timeout at 300 ms, while the OS lets the user choose
 * the double-click speed (macOS defaults to 500 ms). Without this, a double-click the OS
 * accepts can reach detectTapGestures as two single taps.
 */
@Composable
fun ProvideSystemDoubleClickInterval(content: @Composable () -> Unit) {
    val base = LocalViewConfiguration.current
    val configuration =
        remember(base) {
            withDoubleTapTimeout(
                base,
                Toolkit.getDefaultToolkit().getDesktopProperty("awt.multiClickInterval"),
            )
        }
    CompositionLocalProvider(LocalViewConfiguration provides configuration, content = content)
}

internal fun withDoubleTapTimeout(
    base: ViewConfiguration,
    multiClickInterval: Any?,
): ViewConfiguration {
    val interval = (multiClickInterval as? Int)?.toLong()
    return if (interval == null || interval <= 0) {
        base
    } else {
        object : ViewConfiguration by base {
            override val doubleTapTimeoutMillis: Long = interval
        }
    }
}
