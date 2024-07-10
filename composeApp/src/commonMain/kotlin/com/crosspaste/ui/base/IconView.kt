package com.crosspaste.ui.base

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.ContentAlpha
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.crosspaste.utils.getPainterUtils
import okio.Path

@Composable
fun PasteIconButton(
    size: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            modifier
                .size(size)
                .clickable(
                    onClick = onClick,
                    enabled = enabled,
                    role = Role.Button,
                    interactionSource = interactionSource,
                    indication = rememberRipple(bounded = false, radius = size / 2 + 2.dp),
                ),
        contentAlignment = Alignment.Center,
    ) {
        val contentAlpha = if (enabled) LocalContentAlpha.current else ContentAlpha.disabled
        CompositionLocalProvider(LocalContentAlpha provides contentAlpha, content = content)
    }
}

@Composable
fun AppImageIcon(
    path: Path,
    isMacStyleIcon: Boolean,
    size: Dp = 24.dp,
) {
    val painter = remember(path.name) { BitmapPainter(getPainterUtils().getImageBitmap(path)) }

    if (isMacStyleIcon) {
        Image(
            painter = painter,
            contentDescription = "Paste Icon",
            modifier = Modifier.size(size),
        )
    } else {
        val mainSize = (size / 24) * 20
        val paddingSize = (size / 24) * 2
        Image(
            painter = painter,
            contentDescription = "Paste Icon",
            modifier = Modifier.padding(paddingSize).size(mainSize),
        )
    }
}
