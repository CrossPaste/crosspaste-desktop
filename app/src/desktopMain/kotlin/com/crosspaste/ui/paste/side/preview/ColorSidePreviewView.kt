package com.crosspaste.ui.paste.side.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.crosspaste.app.AppSize
import com.crosspaste.db.paste.PasteData
import com.crosspaste.paste.item.PasteColor
import com.crosspaste.ui.theme.AppUISize.giant
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.tiny2XRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.xxxLarge
import org.koin.compose.koinInject

@Composable
fun ColorSidePreviewView(pasteData: PasteData) {
    pasteData.getPasteItem(PasteColor::class)?.let { pasteColor ->
        SidePasteLayoutView(
            pasteData = pasteData,
            pasteBottomContent = {
                BottomGradient(pasteColor.toHexString())
            },
        ) {
            Column(
                modifier =
                    Modifier.fillMaxSize()
                        .padding(medium)
                        .padding(bottom = xxxLarge),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val appSize = koinInject<AppSize>()
                Box(
                    modifier =
                        Modifier
                            .size(appSize.mainPasteSize.height)
                            .clip(tiny2XRoundedCornerShape)
                            .background(Color(pasteColor.color).copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(giant)
                                .clip(tiny2XRoundedCornerShape)
                                .background(Color(pasteColor.color)),
                    )
                }
            }
        }
    }
}
