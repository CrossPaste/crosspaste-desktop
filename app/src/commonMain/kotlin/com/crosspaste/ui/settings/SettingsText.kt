package com.crosspaste.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.crosspaste.ui.theme.AppUIColors

@Composable
fun SettingsText(
    modifier: Modifier = Modifier,
    text: String,
    color: Color = MaterialTheme.colorScheme.contentColorFor(AppUIColors.settingsBackground),
) {
    Text(
        modifier = modifier,
        text = text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = SettingsTextStyle(color),
    )
}

@Composable
fun SettingsTextStyle(color: Color = MaterialTheme.colorScheme.contentColorFor(AppUIColors.settingsBackground)): TextStyle {
    return MaterialTheme.typography.labelMedium.copy(
        fontWeight = FontWeight.Light,
        fontSize = 14.sp,
        textAlign = TextAlign.Start,
        color = color,
        lineHeight = TextUnit.Unspecified,
    )
}
