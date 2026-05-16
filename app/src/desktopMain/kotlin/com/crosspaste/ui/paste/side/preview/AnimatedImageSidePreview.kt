package com.crosspaste.ui.paste.side.preview

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Size
import com.crosspaste.app.UserAttentionService
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
    // The side preview is mounted only inside the search window
    // (see SideSearchWindowContent), so we gate solely on that surface — the
    // main window's visibility is irrelevant here, and watching it would keep
    // the frame loop running while the GIF is not actually on screen.
    val isAttentive by userAttentionService.isSearchWindowVisible.collectAsState()

    TransparentBackground(modifier = Modifier.matchParentSize())

    SkiaAnimatedImageView(
        path = imagePath,
        targetSizePx = targetSizePx,
        smartImageDisplayStrategy = smartImageDisplayStrategy,
        isPlaying = isAttentive,
        modifier = Modifier.fillMaxSize().clipToBounds(),
    )
}
