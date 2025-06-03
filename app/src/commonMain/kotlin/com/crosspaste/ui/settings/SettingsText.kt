package com.crosspaste.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUIFont.SettingsTextStyle

@Composable
fun SettingsText(
    modifier: Modifier = Modifier,
    text: String,
    color: Color =
        MaterialTheme.colorScheme.contentColorFor(
            AppUIColors.generalBackground,
        ),
) {
    Text(
        modifier = modifier,
        text = text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = SettingsTextStyle(color),
    )
}
