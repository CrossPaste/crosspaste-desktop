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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.crosspaste.i18n.GlobalCopywriter
import org.koin.compose.koinInject

@Composable
fun SettingItemView(
    painter: Painter,
    text: String,
    height: Dp = 40.dp,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable () -> Unit,
) {
    val copywriter = koinInject<GlobalCopywriter>()
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .height(height)
                .padding(horizontal = 12.dp),
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
            SettingsText(copywriter.getText(text))
        }
        Spacer(modifier = Modifier.weight(1f).widthIn(min = 8.dp))

        content()
    }
}
