package com.crosspaste.ui.paste.side.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.crosspaste.app.AppSize
import com.crosspaste.db.paste.PasteData
import com.crosspaste.paste.item.PasteUrl
import com.crosspaste.ui.base.PasteUrlIcon
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.xxxLarge
import org.koin.compose.koinInject

@Composable
fun UrlSidePreviewView(pasteData: PasteData) {
    pasteData.getPasteItem(PasteUrl::class)?.let { pasteUrl ->
        SidePasteLayoutView(
            pasteData = pasteData,
            pasteBottomContent = {
                BottomSolid(url = pasteUrl.url)
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
                PasteUrlIcon(
                    pasteData = pasteData,
                    iconColor =
                        MaterialTheme.colorScheme.contentColorFor(
                            AppUIColors.pasteBackground,
                        ),
                    size = appSize.mainPasteSize.height,
                )
            }
        }
    }
}
