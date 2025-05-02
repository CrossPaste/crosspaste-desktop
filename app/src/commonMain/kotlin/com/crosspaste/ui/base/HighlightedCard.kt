package com.crosspaste.ui.base

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

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
        elevation =
            CardDefaults.cardElevation(
                defaultElevation = 0.8.dp,
                pressedElevation = 1.dp,
                hoveredElevation = 3.dp,
            ),
        border =
            BorderStroke(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
            ),
    ) {
        content()
    }
}
