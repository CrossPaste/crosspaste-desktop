package com.crosspaste.ui.paste.side

import androidx.compose.foundation.border
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropTransferAction
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.DragAndDropTransferable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.paste.DesktopPasteMenuService
import com.crosspaste.paste.DesktopWriteTransferable
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.TransferableProducer
import com.crosspaste.ui.LocalDesktopAppSizeValueState
import com.crosspaste.ui.base.HighlightedCard
import com.crosspaste.ui.model.FocusedElement
import com.crosspaste.ui.model.PasteSelectionViewModel
import com.crosspaste.ui.paste.PasteDataScope
import com.crosspaste.ui.paste.preview.PasteContextMenuView
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUISize.small3XRoundedCornerShape
import kotlinx.coroutines.runBlocking
import org.koin.compose.koinInject

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PasteDataScope.SidePasteItemView(
    selected: Boolean,
    onPress: () -> Unit,
    onDoubleTap: () -> Unit,
    pasteContent: @Composable PasteData.() -> Unit,
) {
    val configManager = koinInject<CommonConfigManager>()
    val pasteMenuService = koinInject<DesktopPasteMenuService>()
    val pasteProducer = koinInject<TransferableProducer>()
    val pasteSelectionViewModel = koinInject<PasteSelectionViewModel>()
    val focusedElement by pasteSelectionViewModel.focusedElement.collectAsState()

    val appSizeValue = LocalDesktopAppSizeValueState.current

    val graphicsLayer = rememberGraphicsLayer()

    Row(
        modifier =
            Modifier
                .dragAndDropSource(
                    drawDragDecoration = {
                        drawLayer(graphicsLayer)
                    },
                ) { offset ->
                    DragAndDropTransferData(
                        transferable =
                            DragAndDropTransferable(
                                runBlocking {
                                    pasteProducer
                                        .produce(
                                            pasteData = pasteData,
                                            localOnly = true,
                                            primary = configManager.getCurrentConfig().pastePrimaryTypeOnly,
                                        )?.let {
                                            it as DesktopWriteTransferable
                                        } ?: DesktopWriteTransferable(LinkedHashMap())
                                },
                            ),
                        supportedActions =
                            listOf(
                                DragAndDropTransferAction.Copy,
                            ),
                        dragDecorationOffset = offset,
                        onTransferCompleted = {},
                    )
                }.size(appSizeValue.sidePasteSize)
                .clip(small3XRoundedCornerShape)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            onPress()
                            tryAwaitRelease()
                        },
                        onDoubleTap = {
                            onDoubleTap()
                        },
                    )
                }.border(
                    3.dp,
                    if (selected) {
                        if (focusedElement == FocusedElement.PASTE_LIST) {
                            AppUIColors.importantColor
                        } else {
                            AppUIColors.lightBorderColor
                        }
                    } else {
                        Color.Transparent
                    },
                    small3XRoundedCornerShape,
                ),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        graphicsLayer.record {
                            this@drawWithContent.drawContent()
                        }
                        drawLayer(graphicsLayer)
                    },
        ) {
            HighlightedCard(
                modifier =
                    Modifier.fillMaxSize(),
                shape = small3XRoundedCornerShape,
                colors =
                    CardDefaults.cardColors(
                        containerColor = AppUIColors.pasteBackground,
                    ),
            ) {
                PasteContextMenuView(
                    items = pasteMenuService.sidePasteMenuItemsProvider(this@SidePasteItemView),
                ) {
                    pasteData.pasteContent()
                }
            }
        }
    }
}
