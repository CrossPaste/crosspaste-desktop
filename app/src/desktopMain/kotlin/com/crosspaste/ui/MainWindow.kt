package com.crosspaste.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults.rememberTooltipPositionProvider
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowScope
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Close
import com.composables.icons.materialsymbols.rounded.Push_pin
import com.composables.icons.materialsymbols.roundedfilled.Push_pin
import com.crosspaste.app.DesktopAppLaunchState
import com.crosspaste.app.DesktopAppSize
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.cli.CliSymlinkService
import com.crosspaste.cli.CliSymlinkState
import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.platform.Platform
import com.crosspaste.platform.windows.api.Dwmapi
import com.crosspaste.ui.DesktopContext.MainWindowContext
import com.crosspaste.ui.base.GeneralIconButton
import com.crosspaste.ui.settings.GrantAccessibilityDialog
import com.crosspaste.ui.settings.InstallCliDialog
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUISize.large2X
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.tiny2XRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.tiny5X
import com.crosspaste.ui.theme.AppUISize.xxLarge
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.platform.win32.WinDef
import org.koin.compose.koinInject

// Horizontal space reserved on macOS for the system traffic light buttons.
private val MAC_TRAFFIC_LIGHT_INSET = 78.dp

// Width of the custom close button hot zone on Windows/Linux.
private val CLOSE_BUTTON_WIDTH = 48.dp

@Composable
fun MainWindow(windowIcon: Painter?) {
    val appWindowManager = koinInject<DesktopAppWindowManager>()
    val platform = koinInject<Platform>()
    val screenProvider = koinInject<DesktopScreenProvider>()

    val alwaysOnTop by appWindowManager.alwaysOnTopMainWindow.collectAsState()
    val mainWindowInfo by appWindowManager.mainWindowInfo.collectAsState()

    val isMacos = remember { platform.isMacos() }

    Window(
        onCloseRequest = {
            appWindowManager.hideMainWindow()
        },
        visible = mainWindowInfo.show,
        state = mainWindowInfo.state,
        title = appWindowManager.mainWindowTitle,
        icon = windowIcon,
        alwaysOnTop = alwaysOnTop,
        resizable = false,
        // Keep native decoration on macOS so the system can draw the traffic lights;
        // Windows/Linux paint the whole title bar themselves.
        undecorated = !isMacos,
    ) {
        DisplayScaleContent {
            DisposableEffect(Unit) {
                appWindowManager.mainComposeWindow = window
                applyPlatformWindowStyle(window, platform)

                onDispose {
                    appWindowManager.mainComposeWindow = null
                }
            }

            Box(modifier = Modifier.fillMaxSize().mainWindowOutline(platform)) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    MainWindowTitleBar(
                        isMacos = isMacos,
                        alwaysOnTop = alwaysOnTop,
                        onToggleAlwaysOnTop = { appWindowManager.switchAlwaysOnTopMainWindow() },
                        onClose = { appWindowManager.hideMainWindow() },
                    )

                    if (isMacos) {
                        DesktopMenuBar()
                    }

                    MainWindowContext(mainWindowInfo) {
                        CrossPasteMainWindowContent()
                        StartupPrompts(isMacos = isMacos)
                    }
                }

                screenProvider.DragTargetView()
            }
        }
    }
}

/**
 * Native window tweaks that have to happen on the AWT window itself, right
 * after it is created: macOS extends Compose into the system title bar,
 * Windows asks DWM for rounded corners. Linux needs nothing here.
 */
private fun applyPlatformWindowStyle(
    window: ComposeWindow,
    platform: Platform,
) {
    when {
        platform.isMacos() -> {
            // JBR / Apple JDK client properties:
            // - fullWindowContent: extend Compose content into the title bar
            // - transparentTitleBar: make the system title bar background transparent
            // - windowTitleVisible: hide the system-drawn title text
            window.rootPane.apply {
                putClientProperty("apple.awt.fullWindowContent", true)
                putClientProperty("apple.awt.transparentTitleBar", true)
                putClientProperty("apple.awt.windowTitleVisible", false)
            }
        }
        platform.isWindows() -> {
            // Apply rounded corners on Windows (Win11 Build 22000+, silently ignored on older versions)
            applyWindowsRoundedCorners(window)
        }
    }
}

/**
 * Linux: the undecorated window gets neither a compositor border nor a
 * shadow, so a light outline keeps it from merging with other windows.
 */
@Composable
private fun Modifier.mainWindowOutline(platform: Platform): Modifier =
    if (platform.isLinux()) {
        border(tiny5X, AppUIColors.sectionCardBorder)
    } else {
        this
    }

/**
 * Title bar row shared by all platforms:
 *   - macOS: pin only; padding(start) reserves space for the traffic lights and
 *     the system frame already provides dragging.
 *   - Windows/Linux: pin followed by a full-height close button on the right,
 *     wrapped in a Compose drag area because the window is undecorated.
 */
@Composable
private fun WindowScope.MainWindowTitleBar(
    isMacos: Boolean,
    alwaysOnTop: Boolean,
    onToggleAlwaysOnTop: () -> Unit,
    onClose: () -> Unit,
) {
    val appSizeValue = LocalDesktopAppSizeValueState.current

    val titleBar: @Composable () -> Unit = {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(appSizeValue.windowDecorationHeight)
                    .background(AppUIColors.contentBackground)
                    .padding(start = if (isMacos) MAC_TRAFFIC_LIGHT_INSET else 0.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.End,
        ) {
            AlwaysOnTopButton(
                alwaysOnTop = alwaysOnTop,
                onClick = onToggleAlwaysOnTop,
            )

            if (!isMacos) {
                TitleBarCloseButton(
                    modifier =
                        Modifier
                            .width(CLOSE_BUTTON_WIDTH)
                            .fillMaxHeight(),
                    onClick = onClose,
                )
            }
        }
    }

    if (isMacos) {
        titleBar()
    } else {
        WindowDraggableArea { titleBar() }
    }
}

@Composable
private fun AlwaysOnTopButton(
    alwaysOnTop: Boolean,
    onClick: () -> Unit,
) {
    val appSize = koinInject<DesktopAppSize>()
    val pushpinPadding = remember { appSize.getPinPushEndPadding() }

    GeneralIconButton(
        modifier =
            Modifier.padding(
                top = medium,
                end = pushpinPadding,
            ),
        imageVector =
            if (alwaysOnTop) {
                MaterialSymbols.RoundedFilled.Push_pin
            } else {
                MaterialSymbols.Rounded.Push_pin
            },
        desc = "always_on_top",
        colors =
            IconButtonDefaults.iconButtonColors(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        buttonSize = xxLarge,
        iconSize = large2X,
        shape = tiny2XRoundedCornerShape,
        onClick = onClick,
    )
}

/**
 * One-time prompts shown over the main content. The accessibility dialog
 * wins; the CLI install offer (macOS only, D6 PATH integration, symlink
 * state probed once at app startup) waits until it is dismissed so the two
 * never stack.
 */
@Composable
private fun StartupPrompts(isMacos: Boolean) {
    val appLaunchState = koinInject<DesktopAppLaunchState>()
    val configManager = koinInject<DesktopConfigManager>()
    val config by configManager.config.collectAsState()

    when {
        config.showGrantAccessibility && !appLaunchState.accessibilityPermissions -> {
            GrantAccessibilityDialog {
                configManager.updateConfig("showGrantAccessibility", false)
            }
        }
        isMacos && config.showInstallCliPrompt -> {
            val cliSymlinkService = koinInject<CliSymlinkService>()
            val cliSymlinkState by cliSymlinkService.state.collectAsState()
            if (cliSymlinkState == CliSymlinkState.NOT_INSTALLED) {
                InstallCliDialog {
                    configManager.updateConfig("showInstallCliPrompt", false)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TitleBarCloseButton(
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val copywriter = koinInject<GlobalCopywriter>()

    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val containerColor by animateColorAsState(
        targetValue =
            if (isHovered) {
                MaterialTheme.colorScheme.error
            } else {
                Color.Transparent
            },
        label = "closeButtonBackground",
    )
    val iconColor by animateColorAsState(
        targetValue =
            if (isHovered) {
                MaterialTheme.colorScheme.onError
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        label = "closeButtonTint",
    )

    val tooltipState = rememberTooltipState()
    val positionProvider = rememberTooltipPositionProvider(TooltipAnchorPosition.Below)

    // Layout modifiers (align/width/fillMaxHeight) must be on TooltipBox itself
    // — TooltipBox is the actual child of the parent BoxScope, so ParentData
    // like Alignment.TopEnd has to attach there to take effect. The inner Box
    // then fills the slot TooltipBox lays out and owns hover/click visuals.
    TooltipBox(
        positionProvider = positionProvider,
        tooltip = {
            PlainTooltip {
                Text(copywriter.getText("hide_window"))
            }
        },
        state = tooltipState,
        modifier = modifier,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(containerColor)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = MaterialSymbols.Rounded.Close,
                contentDescription = copywriter.getText("hide_window"),
                modifier = Modifier.size(large2X),
                tint = iconColor,
            )
        }
    }
}

private fun applyWindowsRoundedCorners(window: java.awt.Window) {
    val hwnd = WinDef.HWND(Native.getWindowPointer(window))
    val cornerPreference = Memory(4).apply { setInt(0, Dwmapi.DWMWCP_ROUND) }
    Dwmapi.INSTANCE.DwmSetWindowAttribute(
        hwnd,
        Dwmapi.DWMWA_WINDOW_CORNER_PREFERENCE,
        cornerPreference,
        4,
    )
}
