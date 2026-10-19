package com.crosspaste.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object CrossPasteBrand {

    // Same top-to-bottom blue gradient as the app icon artwork.
    val gradient: Brush =
        Brush.verticalGradient(
            0f to Color(0xFF3588FC),
            0.5f to Color(0xFF1758FE),
            1f to Color(0xFF0842FC),
        )
}
