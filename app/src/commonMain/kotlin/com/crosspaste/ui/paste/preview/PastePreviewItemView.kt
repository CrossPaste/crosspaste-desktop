package com.crosspaste.ui.paste.preview

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.crosspaste.app.AppSize
import com.crosspaste.ui.base.HighlightedCard
import com.crosspaste.ui.paste.PasteDataScope
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUISize.tiny2XRoundedCornerShape
import org.koin.compose.koinInject

@Composable
fun PasteDataScope.PastePreviewItemView(content: @Composable PasteDataScope.() -> Unit) {
    val appSize = koinInject<AppSize>()
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(appSize.mainPasteSize.height),
    ) {
        HighlightedCard(
            modifier = Modifier.fillMaxSize(),
            shape = tiny2XRoundedCornerShape,
            colors =
                CardDefaults.cardColors(
                    containerColor = AppUIColors.pasteBackground,
                ),
        ) {
            content()
        }
    }
}
