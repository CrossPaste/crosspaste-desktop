package com.crosspaste.ui.paste.preview

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.crosspaste.db.paste.PasteData
import com.crosspaste.ui.base.HighlightedCard
import com.crosspaste.ui.theme.AppUISize.tiny2X

@Composable
fun PastePreviewItemView(
    pasteData: PasteData,
    pasteContent: @Composable PasteData.() -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(100.dp),
    ) {
        HighlightedCard(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(tiny2X),
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            pasteData.pasteContent()
        }
    }
}
