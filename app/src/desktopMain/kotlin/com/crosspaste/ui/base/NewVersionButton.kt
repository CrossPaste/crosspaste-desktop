package com.crosspaste.ui.base

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.crosspaste.app.AppUpdateService
import org.koin.compose.koinInject

@Composable
fun NewVersionButton(modifier: Modifier = Modifier) {
    val appUpdateService = koinInject<AppUpdateService>()
    Row(
        modifier =
            modifier
                .wrapContentWidth()
                .height(20.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.Red)
                .clickable {
                    appUpdateService.jumpDownload()
                }
                .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "new!",
            color = Color.White,
            style =
                MaterialTheme.typography.labelSmall
                    .copy(fontStyle = FontStyle.Italic, lineHeight = TextUnit.Unspecified),
        )
    }
}
