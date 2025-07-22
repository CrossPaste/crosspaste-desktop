package com.crosspaste.ui.paste.side.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.crosspaste.db.paste.PasteData
import com.crosspaste.paste.item.PasteColor
import com.crosspaste.ui.theme.DesktopAppUIColors
import com.crosspaste.utils.ColorUtils

@Composable
fun ColorSidePreviewView(pasteData: PasteData) {
    pasteData.getPasteItem(PasteColor::class)?.let { pasteColor ->
        SidePasteLayoutView(
            pasteData = pasteData,
            pasteBottomContent = {},
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color(pasteColor.color)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = pasteColor.toHexString(),
                    style =
                        MaterialTheme.typography.headlineSmall.copy(
                            color =
                                if (ColorUtils.isDarkColor(Color(pasteColor.color))) {
                                    DesktopAppUIColors.sideOnDarkColor
                                } else {
                                    DesktopAppUIColors.sideOnLightColor
                                },
                        ),
                )
            }
        }
    }
}
