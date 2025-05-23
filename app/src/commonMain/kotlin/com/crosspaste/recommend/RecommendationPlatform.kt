package com.crosspaste.recommend

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

interface RecommendationPlatform {
    val platformName: String

    @Composable
    fun ButtonPlatform(onClick: () -> Unit)

    @Composable
    fun ButtonContentView(
        onClick: () -> Unit,
        iconContent: @Composable () -> Unit,
    ) {
        Box(
            modifier = Modifier.size(48.dp),
            contentAlignment = Alignment.Center,
        ) {
            Button(
                modifier =
                    Modifier
                        .fillMaxWidth(),
                onClick = onClick,
                shape = RoundedCornerShape(8.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        contentColor = MaterialTheme.colorScheme.onBackground,
                    ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                contentPadding = PaddingValues(0.dp),
                elevation =
                    ButtonDefaults.elevatedButtonElevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp,
                        hoveredElevation = 0.dp,
                        focusedElevation = 0.dp,
                    ),
            ) {
                iconContent()
            }
        }
    }

    fun action(recommendationService: RecommendationService)
}
