package com.crosspaste.ui.paste.side.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUIFont
import com.crosspaste.ui.theme.AppUISize.tiny4X
import com.crosspaste.ui.theme.DesktopAppUIFont

@Composable
fun FileBottomSolid(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.CenterStart,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start,
        ) {
            val contentColor = MaterialTheme.colorScheme.contentColorFor(AppUIColors.topBackground)

            if (title != null) {
                Text(
                    text = title,
                    style =
                        DesktopAppUIFont.sideTitleTextStyle.copy(contentColor),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = tiny4X),
                )
            }

            val subStyle =
                AppUIFont.mediumBodyTextStyle.copy(
                    color = contentColor.copy(alpha = 0.65f),
                    fontWeight = FontWeight.Normal,
                )

            val displayMaxLines = if (title == null) 2 else 1

            Text(
                text = subtitle,
                style = subStyle,
                maxLines = displayMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
