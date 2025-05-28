package com.crosspaste.ui.paste.preview

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.crosspaste.app.AppSize
import com.crosspaste.ui.theme.AppUISize.tiny2XRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.tiny5X
import com.crosspaste.ui.theme.AppUISize.xxLarge
import org.koin.compose.koinInject

@Composable
fun PasteSpecificPreviewContentView(
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    pasteMainContent: @Composable () -> Unit,
    pasteRightInfo: @Composable ((Boolean) -> Unit) -> Unit,
) {
    val appSize = koinInject<AppSize>()

    var showMenu by remember { mutableStateOf(false) }
    val width =
        animateDpAsState(
            targetValue =
                if (showMenu) {
                    appSize.mainPasteSize.width - 35.dp
                } else {
                    appSize.mainPasteSize.width
                },
        )

    Box(
        modifier =
            Modifier.fillMaxSize(),
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .width(width.value)
                        .clip(tiny2XRoundedCornerShape)
                        .border(tiny5X, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), tiny2XRoundedCornerShape)
                        .background(color = backgroundColor),
            ) {
                pasteMainContent()
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
        ) {
            Spacer(modifier = Modifier.weight(1f))
            Column(
                modifier = Modifier.width(xxLarge),
                verticalArrangement = Arrangement.Top,
            ) {
                pasteRightInfo { showMenu = it }
            }
        }
    }
}
