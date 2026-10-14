package com.crosspaste.ui.base

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.crosspaste.ui.theme.CrossPasteBrand

@Composable
expect fun CrossPasteLogoView(
    size: Dp,
    modifier: Modifier = Modifier,
    enableDebugToggle: Boolean = false,
)

/** The CrossPaste logo: the complete white glyph on the brand-gradient tile. */
@Composable
fun CrossPasteLogo(
    size: Dp,
    modifier: Modifier = Modifier,
) {
    LogoTile(size, modifier) { glyphModifier ->
        CrossPasteGlyph(progress = { 1f }, color = Color.White, modifier = glyphModifier)
    }
}

/** [CrossPasteLogo] whose glyph traces itself once each time it enters the composition. */
@Composable
fun AnimatedCrossPasteLogo(
    size: Dp,
    modifier: Modifier = Modifier,
) {
    LogoTile(size, modifier) { glyphModifier ->
        AnimatedCrossPasteGlyph(color = Color.White, modifier = glyphModifier)
    }
}

@Composable
private fun LogoTile(
    size: Dp,
    modifier: Modifier,
    glyph: @Composable (Modifier) -> Unit,
) {
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(size / 4))
                .background(CrossPasteBrand.gradient)
                .size(size)
                .semantics { contentDescription = "CrossPaste Logo" },
    ) {
        glyph(
            Modifier
                .fillMaxSize()
                .padding(size * 0.1f),
        )
    }
}
