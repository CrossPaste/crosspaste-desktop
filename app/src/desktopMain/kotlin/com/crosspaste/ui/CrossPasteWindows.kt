package com.crosspaste.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import com.crosspaste.app.DesktopAppSize
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.CrossPasteTheme.Theme
import com.crosspaste.ui.theme.ThemeDetector
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.intui.standalone.theme.darkThemeDefinition
import org.jetbrains.jewel.intui.standalone.theme.default
import org.jetbrains.jewel.intui.standalone.theme.lightThemeDefinition
import org.jetbrains.jewel.intui.window.decoratedWindow
import org.jetbrains.jewel.intui.window.styling.dark
import org.jetbrains.jewel.intui.window.styling.defaults
import org.jetbrains.jewel.intui.window.styling.light
import org.jetbrains.jewel.ui.ComponentStyling
import org.jetbrains.jewel.window.styling.TitleBarColors
import org.jetbrains.jewel.window.styling.TitleBarMetrics
import org.jetbrains.jewel.window.styling.TitleBarStyle
import org.koin.compose.koinInject

@Composable
fun CrossPasteWindows(windowIcon: Painter?) {
    val appSize = koinInject<DesktopAppSize>()
    val themeDetector = koinInject<ThemeDetector>()

    Theme {
        IntUiTheme(
            theme =
                if (themeDetector.isCurrentThemeDark()) {
                    JewelTheme.darkThemeDefinition()
                } else {
                    JewelTheme.lightThemeDefinition()
                },
            styling =
                ComponentStyling.default().decoratedWindow(
                    titleBarStyle =
                        if (themeDetector.isCurrentThemeDark()) {
                            TitleBarStyle.dark(
                                colors =
                                    TitleBarColors.dark(
                                        backgroundColor = AppUIColors.appBackground,
                                        inactiveBackground = AppUIColors.appBackground,
                                        borderColor = AppUIColors.appBackground,
                                    ),
                                metrics =
                                    TitleBarMetrics.defaults(height = appSize.windowDecorationHeight),
                            )
                        } else {
                            TitleBarStyle.light(
                                colors =
                                    TitleBarColors.light(
                                        backgroundColor = AppUIColors.appBackground,
                                        inactiveBackground = AppUIColors.appBackground,
                                        borderColor = AppUIColors.appBackground,
                                    ),
                                metrics =
                                    TitleBarMetrics.defaults(height = appSize.windowDecorationHeight),
                            )
                        },
                ),
            swingCompatMode = false,
        ) {
            MainWindow(windowIcon)

            SearchWindow(windowIcon)
        }
    }
}
