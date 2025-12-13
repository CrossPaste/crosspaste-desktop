package com.crosspaste.ui.base

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import com.crosspaste.ui.theme.AppUIFont
import com.crosspaste.ui.theme.AppUISize.small2X
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.tiny2X
import com.crosspaste.ui.theme.AppUISize.tiny3X

@Composable
fun BoxScope.ImageResolution(imageSize: IntSize) {
    Box(
        modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = small2X)
                .background(
                    color = Color.Black.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(tiny2X),
                ).padding(horizontal = tiny, vertical = tiny3X),
    ) {
        Text(
            text = "${imageSize.width} × ${imageSize.height}",
            color = Color.White.copy(alpha = 0.9f),
            style = AppUIFont.imageResolutionTextStyle,
        )
    }
}
