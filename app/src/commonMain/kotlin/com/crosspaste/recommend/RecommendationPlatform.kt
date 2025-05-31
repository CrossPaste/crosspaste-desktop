package com.crosspaste.recommend

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.crosspaste.ui.theme.AppUISize.tiny5X
import com.crosspaste.ui.theme.AppUISize.tinyRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.xxxxLarge
import com.crosspaste.ui.theme.AppUISize.zero
import com.crosspaste.ui.theme.AppUISize.zeroButtonElevation

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
            modifier = Modifier.size(xxxxLarge),
            contentAlignment = Alignment.Center,
        ) {
            Button(
                modifier =
                    Modifier
                        .fillMaxWidth(),
                onClick = onClick,
                shape = tinyRoundedCornerShape,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black,
                    ),
                border = BorderStroke(tiny5X, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                contentPadding = PaddingValues(zero),
                elevation = zeroButtonElevation,
            ) {
                iconContent()
            }
        }
    }

    fun action(recommendationService: RecommendationService)
}
