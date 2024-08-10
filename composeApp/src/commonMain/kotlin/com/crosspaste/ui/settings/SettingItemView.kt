package com.crosspaste.ui.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crosspaste.LocalKoinApplication
import com.crosspaste.i18n.GlobalCopywriter

@Composable
fun SettingItemView(
    painter: Painter,
    text: String,
    tint: Color = MaterialTheme.colors.primary,
    content: @Composable () -> Unit,
) {
    val current = LocalKoinApplication.current
    val copywriter = current.koin.get<GlobalCopywriter>()
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .height(40.dp)
                .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            modifier = Modifier.size(15.dp),
            painter = painter,
            contentDescription = text,
            tint = tint,
        )

        Spacer(modifier = Modifier.width(8.dp))

        Row(
            modifier =
                Modifier.wrapContentWidth()
                    .horizontalScroll(rememberScrollState()),
        ) {
            settingsText(copywriter.getText(text))
        }
        Spacer(modifier = Modifier.weight(1f).widthIn(min = 8.dp))

        content()
    }
}

@Composable
fun settingsText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        modifier = modifier,
        text = text,
        color = MaterialTheme.colors.onBackground,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style =
            TextStyle(
                fontSize = 14.sp,
                fontWeight = FontWeight.Light,
                fontFamily = FontFamily.SansSerif,
            ),
    )
}
