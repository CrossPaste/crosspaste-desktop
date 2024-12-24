package com.crosspaste.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.ThemeDetector
import com.crosspaste.ui.theme.CoralColor
import com.crosspaste.ui.theme.GrassColor
import com.crosspaste.ui.theme.HoneyColor
import com.crosspaste.ui.theme.SeaColor
import org.koin.compose.koinInject

@Composable
fun ThemeSegmentedControl() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ThemeStyle()
        ThemeColor()
    }
}

@Composable
private fun ThemeStyle() {
    val copywriter = koinInject<GlobalCopywriter>()
    val themeDetector = koinInject<ThemeDetector>()

    Row(verticalAlignment = Alignment.CenterVertically) {
        // Light Button
        Button(
            modifier = Modifier.height(28.dp),
            onClick = {
                themeDetector.setThemeConfig(
                    isFollowSystem = false,
                    isUserInDark = false,
                )
            },
            // Apply the shape only to the left side for the first button
            shape =
                RoundedCornerShape(
                    topStart = 4.dp,
                    bottomStart = 4.dp,
                    topEnd = 0.dp,
                    bottomEnd = 0.dp,
                ),
            // Change the background and content colors based on selection
            colors =
                if (!themeDetector.isFollowSystem() && !themeDetector.isCurrentThemeDark()) {
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                } else {
                    ButtonDefaults.buttonColors(containerColor = Color.White)
                },
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
            Text(
                copywriter.getText("light"),
                fontSize = 14.sp,
                fontFamily = FontFamily.SansSerif,
                style = TextStyle(fontWeight = FontWeight.Light),
                color = if (!themeDetector.isFollowSystem() && !themeDetector.isCurrentThemeDark()) Color.White else Color.Black,
            )
        }

        // System Button
        Button(
            modifier = Modifier.height(28.dp).offset(x = (-1).dp),
            onClick = { themeDetector.setThemeConfig(isFollowSystem = true) },
            // No shape for the middle button to keep it rectangular
            shape = RoundedCornerShape(0.dp),
            colors =
                if (themeDetector.isFollowSystem()) {
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                } else {
                    ButtonDefaults.buttonColors(containerColor = Color.White)
                },
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
            Text(
                copywriter.getText("system"),
                fontSize = 14.sp,
                fontFamily = FontFamily.SansSerif,
                style = TextStyle(fontWeight = FontWeight.Light),
                color = if (themeDetector.isFollowSystem()) Color.White else Color.Black,
            )
        }

        // Dark Button
        Button(
            modifier = Modifier.height(28.dp).offset(x = (-2).dp),
            onClick = {
                themeDetector.setThemeConfig(
                    isFollowSystem = false,
                    isUserInDark = true,
                )
            },
            // Apply the shape only to the right side for the last button
            shape =
                RoundedCornerShape(
                    topStart = 0.dp,
                    bottomStart = 0.dp,
                    topEnd = 4.dp,
                    bottomEnd = 4.dp,
                ),
            colors =
                if (!themeDetector.isFollowSystem() && themeDetector.isCurrentThemeDark()) {
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                } else {
                    ButtonDefaults.buttonColors(containerColor = Color.White)
                },
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
            Text(
                copywriter.getText("dark"),
                fontSize = 14.sp,
                fontFamily = FontFamily.SansSerif,
                style = TextStyle(fontWeight = FontWeight.Light),
                color = if (!themeDetector.isFollowSystem() && themeDetector.isCurrentThemeDark()) Color.White else Color.Black,
            )
        }
    }
}

@Composable
private fun ThemeColor() {
    val themeDetector = koinInject<ThemeDetector>()

    Row(verticalAlignment = Alignment.CenterVertically) {
        // CoralColor
        Button(
            modifier = Modifier.height(28.dp),
            onClick = {
                themeDetector.setThemeColor(CoralColor)
            },
            // Apply the shape only to the left side for the first button
            shape =
                RoundedCornerShape(
                    topStart = 4.dp,
                    bottomStart = 4.dp,
                    topEnd = 0.dp,
                    bottomEnd = 0.dp,
                ),
            // Change the background and content colors based on selection
            colors =
                ButtonDefaults.buttonColors(
                    containerColor =
                        if (themeDetector.isCurrentThemeDark()) {
                            CoralColor.darkColorScheme.primary
                        } else {
                            CoralColor.lightColorScheme.primary
                        },
                ),
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
            if (themeDetector.isThemeColor(CoralColor)) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = Color.White,
                )
            }
        }

        // GrassColor
        Button(
            modifier = Modifier.height(28.dp).offset(x = (-1).dp),
            onClick = {
                themeDetector.setThemeColor(GrassColor)
            },
            // No shape for the middle button to keep it rectangular
            shape = RoundedCornerShape(0.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor =
                        if (themeDetector.isCurrentThemeDark()) {
                            GrassColor.darkColorScheme.primary
                        } else {
                            GrassColor.lightColorScheme.primary
                        },
                ),
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
            if (themeDetector.isThemeColor(GrassColor)) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = Color.White,
                )
            }
        }

        // SeaColor
        Button(
            modifier = Modifier.height(28.dp).offset(x = (-1).dp),
            onClick = {
                themeDetector.setThemeColor(SeaColor)
            },
            // No shape for the middle button to keep it rectangular
            shape = RoundedCornerShape(0.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor =
                        if (themeDetector.isCurrentThemeDark()) {
                            SeaColor.darkColorScheme.primary
                        } else {
                            SeaColor.lightColorScheme.primary
                        },
                ),
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
            if (themeDetector.isThemeColor(SeaColor)) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = Color.White,
                )
            }
        }

        // HoneyColor
        Button(
            modifier = Modifier.height(28.dp).offset(x = (-2).dp),
            onClick = {
                themeDetector.setThemeColor(HoneyColor)
            },
            // Apply the shape only to the right side for the last button
            shape =
                RoundedCornerShape(
                    topStart = 0.dp,
                    bottomStart = 0.dp,
                    topEnd = 4.dp,
                    bottomEnd = 4.dp,
                ),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor =
                        if (themeDetector.isCurrentThemeDark()) {
                            HoneyColor.darkColorScheme.primary
                        } else {
                            HoneyColor.lightColorScheme.primary
                        },
                ),
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
            if (themeDetector.isThemeColor(HoneyColor)) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = Color.White,
                )
            }
        }
    }
}
