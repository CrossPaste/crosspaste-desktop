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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.crosspaste.ui.LocalDesktopAppSizeValueState
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUISize.tiny2XRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.tiny5X
import com.crosspaste.ui.theme.AppUISize.xxLarge
import com.crosspaste.ui.theme.AppUISize.xxxLarge

@Composable
fun PasteSpecificPreviewContentView(
    background: Color = AppUIColors.pasteBackground,
    pasteMainContent: @Composable () -> Unit,
    pasteRightInfo: @Composable ((Boolean) -> Unit) -> Unit,
) {
    val appSizeValue = LocalDesktopAppSizeValueState.current

    var showMenu by remember { mutableStateOf(false) }
    val width =
        animateDpAsState(
            targetValue =
                if (showMenu) {
                    appSizeValue.mainPasteSize.width - xxxLarge
                } else {
                    appSizeValue.mainPasteSize.width
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
                        .border(tiny5X, AppUIColors.lightBorderColor, tiny2XRoundedCornerShape)
                        .background(background),
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
