package com.crosspaste.ui.base

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import com.crosspaste.app.generated.resources.Res
import com.crosspaste.app.generated.resources.crosspaste_svg
import org.jetbrains.compose.resources.painterResource

@Composable
actual fun CrossPasteLogoView(
    size: Dp,
    color: Color,
) {
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(size / 4))
                .background(color)
                .size(size)
                .onSizeChanged { boxSize = it },
    ) {
        val paddingPercent = 0.1f
        val paddingPx = minOf(boxSize.width, boxSize.height) * paddingPercent
        val paddingDp = with(LocalDensity.current) { paddingPx.toDp() }

        Icon(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingDp),
            painter = painterResource(Res.drawable.crosspaste_svg),
            tint = MaterialTheme.colorScheme.onPrimary,
            contentDescription = "CrossPaste Logo",
        )
    }
}
