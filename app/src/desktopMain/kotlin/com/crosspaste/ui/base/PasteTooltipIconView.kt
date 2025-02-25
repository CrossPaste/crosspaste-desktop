package com.crosspaste.ui.base

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun PasteTooltipIconView(
    painter: Painter,
    background: Color = MaterialTheme.colorScheme.surface,
    hover: Color = MaterialTheme.colorScheme.primaryContainer,
    tint: Color? = null,
    text: String,
    contentDescription: String = "",
    onClick: () -> Unit,
) {
    var hoverIcon by remember { mutableStateOf(false) }

    PasteTooltipAreaView(
        modifier = Modifier.size(32.dp),
        text = text,
    ) {
        Box(
            modifier =
                Modifier.size(32.dp)
                    .onPointerEvent(
                        eventType = PointerEventType.Enter,
                        onEvent = {
                            hoverIcon = true
                        },
                    )
                    .onPointerEvent(
                        eventType = PointerEventType.Exit,
                        onEvent = {
                            hoverIcon = false
                        },
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier.fillMaxSize()
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (hoverIcon) {
                                hover
                            } else {
                                Color.Transparent
                            },
                        )
                        .pointerInput(Unit) {
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
                modifier = Modifier.size(20.dp),
                tint =
                    tint ?: if (hoverIcon) {
                        MaterialTheme.colorScheme.contentColorFor(hover)
                    } else {
                        MaterialTheme.colorScheme.contentColorFor(background)
                    },
            )
        }
    }
}
