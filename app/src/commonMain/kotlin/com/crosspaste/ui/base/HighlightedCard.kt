package com.crosspaste.ui.base

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUISize.highlightedCardElevation
import com.crosspaste.ui.theme.AppUISize.tiny6X

@Composable
fun HighlightedCard(
    modifier: Modifier,
    shape: RoundedCornerShape,
    colors: CardColors = CardDefaults.cardColors(),
    elevation: CardElevation = highlightedCardElevation,
    border: BorderStroke =
        BorderStroke(
            width = tiny6X,
            color = AppUIColors.lightBorderColor,
        ),
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
    ) {
        content()
    }
}
