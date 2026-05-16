package com.crosspaste.ui.paste.side.preview

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Size
import com.crosspaste.app.AttentionSurface
import com.crosspaste.app.UserAttentionService
import com.crosspaste.app.attentionOn
import com.crosspaste.ui.base.SmartImageDisplayStrategy
import com.crosspaste.ui.base.TransparentBackground
import okio.Path

@Composable
fun BoxScope.AnimatedImageSidePreview(
    imagePath: Path,
    targetSizePx: Size,
    smartImageDisplayStrategy: SmartImageDisplayStrategy,
    userAttentionService: UserAttentionService,
) {
    // Pause animation when neither host window is visible — saves CPU/redraws
    // while still resuming instantly when the user opens either surface.
    val isAttentive by remember(userAttentionService) {
        userAttentionService.attentionOn(
            AttentionSurface.MAIN_WINDOW,
            AttentionSurface.SEARCH_WINDOW,
        )
    }.collectAsState(initial = false)

    TransparentBackground(modifier = Modifier.matchParentSize())

    SkiaAnimatedImageView(
        path = imagePath,
        targetSizePx = targetSizePx,
        smartImageDisplayStrategy = smartImageDisplayStrategy,
        isPlaying = isAttentive,
        modifier = Modifier.fillMaxSize().clipToBounds(),
    )
}
