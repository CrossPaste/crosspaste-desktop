package com.crosspaste.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import com.crosspaste.app.DesktopAppSize
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.tiny
import org.koin.compose.koinInject

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun WindowDecoration(title: String) {
    val appSize = koinInject<DesktopAppSize>()
    val copywriter = koinInject<GlobalCopywriter>()

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(appSize.windowDecorationHeight)
                .offset(y = -appSize.windowDecorationHeight)
                .padding(start = medium)
                .padding(bottom = tiny),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            modifier = Modifier,
            text = copywriter.getText(title),
            color =
                MaterialTheme.colorScheme.contentColorFor(
                    AppUIColors.appBackground,
                ),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
