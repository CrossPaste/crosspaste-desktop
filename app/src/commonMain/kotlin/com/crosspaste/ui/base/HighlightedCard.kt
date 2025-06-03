package com.crosspaste.ui.base

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUISize.highlightedCardElevation
import com.crosspaste.ui.theme.AppUISize.tiny6X

@Composable
fun HighlightedCard(
    modifier: Modifier,
    shape: RoundedCornerShape,
    containerColor: Color,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        shape = shape,
        colors =
            CardDefaults.cardColors(
                containerColor = containerColor,
            ),
        elevation = highlightedCardElevation,
        border =
            BorderStroke(
                width = tiny6X,
                color = AppUIColors.lightBorderColor,
            ),
    ) {
        content()
    }
}
