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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.paste.DesktopReadTransferable
import com.crosspaste.paste.TransferableConsumer
import com.crosspaste.ui.base.clipboard
import com.crosspaste.ui.theme.AppUISize.huge
import com.crosspaste.ui.theme.AppUISize.large2X
import com.crosspaste.ui.theme.AppUISize.massive
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.mediumRoundedCornerShape
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
        targetValue = if (isDragging) 0.8f else 0f,
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

                override fun onDrop(event: DragAndDropEvent): Boolean {
                    val transferable = event.awtTransferable

                    val source: String? = appWindowManager.getCurrentActiveAppName()
                    val pasteTransferable = DesktopReadTransferable(transferable)
                    return runBlocking {
                        pasteConsumer.consume(pasteTransferable, source, false)
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
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = animatedAlpha),
                    ),
        )

        AnimatedVisibility(
            visible = isDragging,
            enter =
                fadeIn(animationSpec = tween(300)) +
                    scaleIn(
                        initialScale = 0.9f,
                        animationSpec = tween(300),
                    ),
            exit =
                fadeOut(animationSpec = tween(300)) +
                    scaleOut(
                        targetScale = 0.9f,
                        animationSpec = tween(300),
                    ),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(horizontal = massive)
                        .clip(mediumRoundedCornerShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(vertical = large2X),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    painter = clipboard(),
                    contentDescription = "clipboard icon",
                    modifier = Modifier.size(huge),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )

                Spacer(modifier = Modifier.height(medium))

                Text(
                    text = copywriter.getText("drop_to_clipboard_and_sync"),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
