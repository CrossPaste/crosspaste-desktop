package com.crosspaste.ui.base

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import com.crosspaste.ui.theme.AppUISize.large2X
import com.crosspaste.ui.theme.AppUISize.tiny2XRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.xxLarge

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun PasteTooltipIconView(
    painter: Painter,
    hover: Color = MaterialTheme.colorScheme.primaryContainer,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onHover: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    text: String,
    contentDescription: String = "",
    opacity: Float = 1f,
    onClick: () -> Unit,
) {
    var hoverIcon by remember { mutableStateOf(false) }

    PasteTooltipAreaView(
        modifier =
            Modifier
                .size(xxLarge)
                .alpha(opacity),
        text = text,
    ) {
        Box(
            modifier =
                Modifier
                    .size(xxLarge)
                    .onPointerEvent(
                        eventType = PointerEventType.Enter,
                        onEvent = {
                            hoverIcon = true
                        },
                    ).onPointerEvent(
                        eventType = PointerEventType.Exit,
                        onEvent = {
                            hoverIcon = false
                        },
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(tiny2XRoundedCornerShape)
                        .background(
                            if (hoverIcon) {
                                hover
                            } else {
                                Color.Transparent
                            },
                        ).pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    onClick()
                                },
                            )
                        },
            ) {}

            Icon(
                painter = painter,
                contentDescription = contentDescription,
                modifier = Modifier.size(large2X),
                tint =
                    if (hoverIcon) {
                        onHover
                    } else {
                        tint
                    },
            )
        }
    }
}
