package com.crosspaste.ui.paste.preview

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.crosspaste.app.AppSize
import com.crosspaste.db.paste.PasteData
import com.crosspaste.ui.base.HighlightedCard
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUISize.tiny2X
import org.koin.compose.koinInject

@Composable
fun PastePreviewItemView(
    pasteData: PasteData,
    pasteContent: @Composable PasteData.() -> Unit,
) {
    val appSize = koinInject<AppSize>()
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(appSize.mainPasteSize.height),
    ) {
        HighlightedCard(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(tiny2X),
            containerColor = AppUIColors.pasteBackground,
        ) {
            pasteData.pasteContent()
        }
    }
}
