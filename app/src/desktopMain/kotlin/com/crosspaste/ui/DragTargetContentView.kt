package com.crosspaste.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Content_paste
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.paste.DesktopReadTransferable
import com.crosspaste.paste.TransferableConsumer
import com.crosspaste.ui.theme.AppUISize.enormous
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.tiny4X
import com.crosspaste.ui.theme.AppUISize.xLarge
import com.crosspaste.ui.theme.AppUISize.xLargeRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.xxLarge
import kotlinx.coroutines.runBlocking
import org.koin.compose.koinInject

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DragTargetContentView() {
    val appWindowManager = koinInject<DesktopAppWindowManager>()
    val copywriter = koinInject<GlobalCopywriter>()
    val pasteConsumer = koinInject<TransferableConsumer>()
    var isDragging by remember { mutableStateOf(false) }
    val animatedAlpha by animateFloatAsState(
        targetValue = if (isDragging) 0.85f else 0f,
        animationSpec = tween(300),
        label = "drag_target_alpha",
    )

    val dragAndDropTarget =
        remember {
            object : DragAndDropTarget {
                override fun onStarted(event: DragAndDropEvent) {
                    isDragging = true
                }

                override fun onEnded(event: DragAndDropEvent) {
                    isDragging = false
                }

                // DragAndDropTarget.onDrop requires a synchronous Boolean return,
                // so runBlocking is unavoidable here due to the framework API contract.
                override fun onDrop(event: DragAndDropEvent): Boolean {
                    val transferable = event.awtTransferable
                    val source: String? = appWindowManager.getCurrentActiveAppName()
                    val pasteTransferable = DesktopReadTransferable(transferable)
                    return runBlocking {
                        pasteConsumer.consume(pasteTransferable, source, remote = false, dragAndDrop = true)
                    }.isSuccess
                }
            }
        }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .dragAndDropTarget(
                    shouldStartDragAndDrop = { true },
                    target = dragAndDropTarget,
                ),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = animatedAlpha),
                    ),
        )

        AnimatedVisibility(
            visible = isDragging,
            enter =
                fadeIn(animationSpec = tween(250)) +
                    scaleIn(
                        initialScale = 0.92f,
                        animationSpec = tween(250),
                    ),
            exit =
                fadeOut(animationSpec = tween(200)) +
                    scaleOut(
                        targetScale = 0.92f,
                        animationSpec = tween(200),
                    ),
            modifier = Modifier.align(Alignment.Center),
        ) {
            val borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
            Surface(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(horizontal = xLarge)
                        .drawWithContent {
                            drawContent()
                            val strokeWidth = tiny4X.toPx()
                            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                            val halfStroke = strokeWidth / 2
                            drawRoundRect(
                                color = borderColor,
                                topLeft = Offset(halfStroke, halfStroke),
                                size =
                                    Size(
                                        width = size.width - strokeWidth,
                                        height = size.height - strokeWidth,
                                    ),
                                style =
                                    Stroke(
                                        width = strokeWidth,
                                        pathEffect = dashEffect,
                                    ),
                                cornerRadius = CornerRadius(xLarge.toPx()),
                            )
                        },
                shape = xLargeRoundedCornerShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = xxLarge, horizontal = medium),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Surface(
                        modifier = Modifier.size(enormous),
                        shape = RoundedCornerShape(xxLarge),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Icon(
                            imageVector = MaterialSymbols.Rounded.Content_paste,
                            contentDescription = null,
                            modifier = Modifier.padding(medium),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }

                    Spacer(modifier = Modifier.height(medium))

                    Text(
                        text = copywriter.getText("drop_to_clipboard_and_sync"),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(modifier = Modifier.height(tiny))

                    Text(
                        text = copywriter.getText("drop_hint"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
