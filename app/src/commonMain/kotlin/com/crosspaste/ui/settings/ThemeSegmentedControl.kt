package com.crosspaste.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.base.check
import com.crosspaste.ui.base.contrastHigh
import com.crosspaste.ui.base.contrastMedium
import com.crosspaste.ui.base.contrastStandard
import com.crosspaste.ui.theme.ColorContrast
import com.crosspaste.ui.theme.CoralColor
import com.crosspaste.ui.theme.GrassColor
import com.crosspaste.ui.theme.HoneyColor
import com.crosspaste.ui.theme.SeaColor
import com.crosspaste.ui.theme.ThemeDetector
import org.koin.compose.koinInject

@Composable
fun ThemeSegmentedControl() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ThemeStyle()
        ContrastColor()
        ThemeColor()
    }
}

@Composable
private fun ThemeButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    shape: Shape,
    buttonColors: ButtonColors,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        modifier = modifier.height(28.dp),
        onClick = onClick,
        // Apply the shape only to the left side for the first button
        shape = shape,
        colors = buttonColors,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceDim),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        elevation =
            ButtonDefaults.elevatedButtonElevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp,
                hoveredElevation = 0.dp,
                focusedElevation = 0.dp,
            ),
    ) {
        content()
    }
}

@Composable
private fun LeftThemeButton(
    onClick: () -> Unit,
    buttonColors: ButtonColors,
    content: @Composable RowScope.() -> Unit,
) {
    ThemeButton(
        onClick = onClick,
        shape =
            RoundedCornerShape(
                topStart = 4.dp,
                bottomStart = 4.dp,
                topEnd = 0.dp,
                bottomEnd = 0.dp,
            ),
        buttonColors = buttonColors,
        content = content,
    )
}

@Composable
private fun MiddleThemeButton(
    onClick: () -> Unit,
    buttonColors: ButtonColors,
    content: @Composable RowScope.() -> Unit,
) {
    ThemeButton(
        onClick = onClick,
        shape = RoundedCornerShape(0.dp),
        buttonColors = buttonColors,
        content = content,
    )
}

@Composable
private fun RightThemeButton(
    onClick: () -> Unit,
    buttonColors: ButtonColors,
    content: @Composable RowScope.() -> Unit,
) {
    ThemeButton(
        onClick = onClick,
        shape =
            RoundedCornerShape(
                topStart = 0.dp,
                bottomStart = 0.dp,
                topEnd = 4.dp,
                bottomEnd = 4.dp,
            ),
        buttonColors = buttonColors,
        content = content,
    )
}

@Composable
private fun ThemeStyle() {
    val copywriter = koinInject<GlobalCopywriter>()
    val themeDetector = koinInject<ThemeDetector>()

    Row(verticalAlignment = Alignment.CenterVertically) {
        // Light Button
        LeftThemeButton(
            onClick = {
                themeDetector.setThemeConfig(
                    isFollowSystem = false,
                    isUserInDark = false,
                )
            },
            // Change the background and content colors based on selection
            buttonColors =
                ButtonDefaults.buttonColors(
                    containerColor =
                        if (!themeDetector.isFollowSystem() && !themeDetector.isCurrentThemeDark()) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            Color.White
                        },
                    contentColor =
                        if (!themeDetector.isFollowSystem() && !themeDetector.isCurrentThemeDark()) {
                            MaterialTheme.colorScheme.contentColorFor(MaterialTheme.colorScheme.primaryContainer)
                        } else {
                            MaterialTheme.colorScheme.contentColorFor(Color.White)
                        },
                ),
        ) {
            Text(
                text = copywriter.getText("light"),
                style = MaterialTheme.typography.labelMedium,
            )
        }

        // System Button
        MiddleThemeButton(
            onClick = { themeDetector.setThemeConfig(isFollowSystem = true) },
            buttonColors =
                ButtonDefaults.buttonColors(
                    containerColor =
                        if (themeDetector.isFollowSystem()) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            Color.White
                        },
                    contentColor =
                        if (themeDetector.isFollowSystem()) {
                            MaterialTheme.colorScheme.contentColorFor(MaterialTheme.colorScheme.primaryContainer)
                        } else {
                            MaterialTheme.colorScheme.contentColorFor(Color.White)
                        },
                ),
        ) {
            Text(
                text = copywriter.getText("system"),
                style = MaterialTheme.typography.labelMedium,
            )
        }

        // Dark Button
        RightThemeButton(
            onClick = {
                themeDetector.setThemeConfig(
                    isFollowSystem = false,
                    isUserInDark = true,
                )
            },
            // Change the background and content colors based on selection
            buttonColors =
                ButtonDefaults.buttonColors(
                    containerColor =
                        if (!themeDetector.isFollowSystem() && themeDetector.isCurrentThemeDark()) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            Color.White
                        },
                    contentColor =
                        if (!themeDetector.isFollowSystem() && themeDetector.isCurrentThemeDark()) {
                            MaterialTheme.colorScheme.contentColorFor(MaterialTheme.colorScheme.primaryContainer)
                        } else {
                            MaterialTheme.colorScheme.contentColorFor(Color.White)
                        },
                ),
        ) {
            Text(
                text = copywriter.getText("dark"),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun ThemeColor() {
    val themeDetector = koinInject<ThemeDetector>()

    val themeColor by themeDetector.currentThemeColor.collectAsState()

    Row(verticalAlignment = Alignment.CenterVertically) {
        // CoralColor
        LeftThemeButton(
            onClick = {
                themeDetector.setThemeColor(CoralColor)
            },
            buttonColors =
                ButtonDefaults.buttonColors(
                    containerColor =
                        if (themeDetector.isCurrentThemeDark()) {
                            CoralColor.darkColorScheme.primary
                        } else {
                            CoralColor.lightColorScheme.primary
                        },
                ),
        ) {
            if (themeColor == CoralColor) {
                Icon(
                    painter = check(),
                    contentDescription = null,
                    tint = Color.White,
                )
            }
        }

        // GrassColor
        MiddleThemeButton(
            onClick = {
                themeDetector.setThemeColor(GrassColor)
            },
            buttonColors =
                ButtonDefaults.buttonColors(
                    containerColor =
                        if (themeDetector.isCurrentThemeDark()) {
                            GrassColor.darkColorScheme.primary
                        } else {
                            GrassColor.lightColorScheme.primary
                        },
                ),
        ) {
            if (themeColor == GrassColor) {
                Icon(
                    painter = check(),
                    contentDescription = null,
                    tint = Color.White,
                )
            }
        }

        // SeaColor
        MiddleThemeButton(
            onClick = {
                themeDetector.setThemeColor(SeaColor)
            },
            buttonColors =
                ButtonDefaults.buttonColors(
                    containerColor =
                        if (themeDetector.isCurrentThemeDark()) {
                            SeaColor.darkColorScheme.primary
                        } else {
                            SeaColor.lightColorScheme.primary
                        },
                ),
        ) {
            if (themeColor == SeaColor) {
                Icon(
                    painter = check(),
                    contentDescription = null,
                    tint = Color.White,
                )
            }
        }

        // HoneyColor
        RightThemeButton(
            onClick = {
                themeDetector.setThemeColor(HoneyColor)
            },
            buttonColors =
                ButtonDefaults.buttonColors(
                    containerColor =
                        if (themeDetector.isCurrentThemeDark()) {
                            HoneyColor.darkColorScheme.primary
                        } else {
                            HoneyColor.lightColorScheme.primary
                        },
                ),
        ) {
            if (themeColor == HoneyColor) {
                Icon(
                    painter = check(),
                    contentDescription = null,
                    tint = Color.White,
                )
            }
        }
    }
}

@Composable
private fun ContrastColor() {
    val themeDetector = koinInject<ThemeDetector>()

    val themeColor by themeDetector.currentThemeColor.collectAsState()

    val colorContrast by themeDetector.colorContrast.collectAsState()

    val leftContainerColor =
        if (colorContrast == ColorContrast.Standard) {
            themeColor.lightColorScheme.primary
        } else {
            themeColor.darkColorScheme.primary
        }

    val onLeftContainerColor =
        if (colorContrast == ColorContrast.Standard) {
            themeColor.lightColorScheme.onPrimary
        } else {
            themeColor.darkColorScheme.onPrimary
        }

    val mediumContainerColor =
        if (colorContrast == ColorContrast.Medium) {
            themeColor.lightMediumContrastColorScheme.primary
        } else {
            themeColor.darkMediumContrastColorScheme.primary
        }

    val onMediumContainerColor =
        if (colorContrast == ColorContrast.Medium) {
            themeColor.lightMediumContrastColorScheme.onPrimary
        } else {
            themeColor.darkMediumContrastColorScheme.onPrimary
        }

    val rightContainerColor =
        if (colorContrast == ColorContrast.High) {
            themeColor.lightHighContrastColorScheme.primary
        } else {
            themeColor.darkHighContrastColorScheme.primary
        }

    val onRightContainerColor =
        if (colorContrast == ColorContrast.High) {
            themeColor.lightHighContrastColorScheme.onPrimary
        } else {
            themeColor.darkHighContrastColorScheme.onPrimary
        }

    Row(verticalAlignment = Alignment.CenterVertically) {
        LeftThemeButton(
            onClick = {
                themeDetector.setColorContrast(ColorContrast.Standard)
            },
            buttonColors =
                ButtonDefaults.buttonColors(
                    containerColor = leftContainerColor,
                ),
        ) {
            Icon(
                painter = contrastStandard(),
                contentDescription = null,
                tint = onLeftContainerColor,
            )
        }

        MiddleThemeButton(
            onClick = {
                themeDetector.setColorContrast(ColorContrast.Medium)
            },
            buttonColors =
                ButtonDefaults.buttonColors(
                    containerColor = mediumContainerColor,
                ),
        ) {
            Icon(
                painter = contrastMedium(),
                contentDescription = null,
                tint = onMediumContainerColor,
            )
        }

        RightThemeButton(
            onClick = {
                themeDetector.setColorContrast(ColorContrast.High)
            },
            buttonColors =
                ButtonDefaults.buttonColors(
                    containerColor = rightContainerColor,
                ),
        ) {
            Icon(
                painter = contrastHigh(),
                contentDescription = null,
                tint = onRightContainerColor,
            )
        }
    }
}
