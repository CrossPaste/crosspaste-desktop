package com.crosspaste.ui.theme

import androidx.compose.runtime.Composable
import com.crosspaste.app.DesktopAppSize
import com.crosspaste.ui.theme.CrossPasteTheme.Theme
import org.jetbrains.jewel.foundation.DisabledAppearanceValues
import org.jetbrains.jewel.foundation.GlobalColors
import org.jetbrains.jewel.foundation.GlobalMetrics
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.intui.core.theme.IntUiLightTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.intui.standalone.theme.createDefaultTextStyle
import org.jetbrains.jewel.intui.standalone.theme.createEditorTextStyle
import org.jetbrains.jewel.intui.standalone.theme.dark
import org.jetbrains.jewel.intui.standalone.theme.darkThemeDefinition
import org.jetbrains.jewel.intui.standalone.theme.default
import org.jetbrains.jewel.intui.standalone.theme.defaults
import org.jetbrains.jewel.intui.standalone.theme.light
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
fun DesktopTheme(content: @Composable () -> Unit) {
    val appSize = koinInject<DesktopAppSize>()
    val themeDetector = koinInject<ThemeDetector>()

    Theme {
        IntUiTheme(
            theme =
                if (themeDetector.isCurrentThemeDark()) {
                    JewelTheme.darkThemeDefinition(
                        colors = GlobalColors.dark(),
                        metrics = GlobalMetrics.defaults(),
                        palette = IntUiDarkTheme.colors,
                        iconData = IntUiDarkTheme.iconData,
                        defaultTextStyle = JewelTheme.createDefaultTextStyle(),
                        editorTextStyle = JewelTheme.createEditorTextStyle(),
                        consoleTextStyle = JewelTheme.createEditorTextStyle(),
                        contentColor = GlobalColors.dark().text.normal,
                        disabledAppearanceValues = DisabledAppearanceValues.dark(),
                    )
                } else {
                    JewelTheme.lightThemeDefinition(
                        colors = GlobalColors.light(),
                        metrics = GlobalMetrics.defaults(),
                        palette = IntUiLightTheme.colors,
                        iconData = IntUiLightTheme.iconData,
                        defaultTextStyle = JewelTheme.createDefaultTextStyle(),
                        editorTextStyle = JewelTheme.createEditorTextStyle(),
                        consoleTextStyle = JewelTheme.createEditorTextStyle(),
                        contentColor = GlobalColors.light().text.normal,
                        disabledAppearanceValues = DisabledAppearanceValues.light(),
                    )
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
            content()
        }
    }
}
