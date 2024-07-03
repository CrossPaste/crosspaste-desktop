package com.crosspaste.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crosspaste.LocalKoinApplication
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.ThemeDetector

@Composable
fun ThemeSegmentedControl() {
    val current = LocalKoinApplication.current
    val copywriter = current.koin.get<GlobalCopywriter>()
    val themeDetector = current.koin.get<ThemeDetector>()

    Row(verticalAlignment = Alignment.CenterVertically) {
        // Light Button
        Button(
            modifier = Modifier.height(28.dp),
            onClick = { themeDetector.setThemeConfig(isFollowSystem = false, isUserInDark = false) },
            // Apply the shape only to the left side for the first button
            shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 0.dp, bottomEnd = 0.dp),
            // Change the background and content colors based on selection
            colors =
                if (!themeDetector.isFollowSystem() && !themeDetector.isCurrentThemeDark()) {
                    ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.primary)
                } else {
                    ButtonDefaults.buttonColors(backgroundColor = Color.White)
                },
            border = BorderStroke(1.dp, Color(0xFFAFCBE1)),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            elevation =
                ButtonDefaults.elevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp,
                    hoveredElevation = 0.dp,
                    focusedElevation = 0.dp,
                ),
        ) {
            Text(
                copywriter.getText("Light"),
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
                    ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.primary)
                } else {
                    ButtonDefaults.buttonColors(backgroundColor = Color.White)
                },
            border = BorderStroke(1.dp, Color(0xFFAFCBE1)),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            elevation =
                ButtonDefaults.elevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp,
                    hoveredElevation = 0.dp,
                    focusedElevation = 0.dp,
                ),
        ) {
            Text(
                copywriter.getText("System"),
                fontSize = 14.sp,
                fontFamily = FontFamily.SansSerif,
                style = TextStyle(fontWeight = FontWeight.Light),
                color = if (themeDetector.isFollowSystem()) Color.White else Color.Black,
            )
        }

        // Dark Button
        Button(
            modifier = Modifier.height(28.dp).offset(x = (-2).dp),
            onClick = { themeDetector.setThemeConfig(isFollowSystem = false, isUserInDark = true) },
            // Apply the shape only to the right side for the last button
            shape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 4.dp, bottomEnd = 4.dp),
            colors =
                if (!themeDetector.isFollowSystem() && themeDetector.isCurrentThemeDark()) {
                    ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.primary)
                } else {
                    ButtonDefaults.buttonColors(backgroundColor = Color.White)
                },
            border = BorderStroke(1.dp, Color(0xFFAFCBE1)),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            elevation =
                ButtonDefaults.elevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp,
                    hoveredElevation = 0.dp,
                    focusedElevation = 0.dp,
                ),
        ) {
            Text(
                copywriter.getText("Dark"),
                fontSize = 14.sp,
                fontFamily = FontFamily.SansSerif,
                style = TextStyle(fontWeight = FontWeight.Light),
                color = if (!themeDetector.isFollowSystem() && themeDetector.isCurrentThemeDark()) Color.White else Color.Black,
            )
        }
    }
}
