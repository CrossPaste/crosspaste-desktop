package com.crosspaste.ui.paste.side

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
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
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.paste.DesktopPasteMenuService
import com.crosspaste.paste.DesktopWriteTransferable
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.TransferableProducer
import com.crosspaste.ui.LocalDesktopAppSizeValueState
import com.crosspaste.ui.base.PasteContextMenuView
import com.crosspaste.ui.model.FocusedElement
import com.crosspaste.ui.model.PasteSelectionViewModel
import com.crosspaste.ui.paste.PasteDataScope
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUISize.highlightedCardElevation
import com.crosspaste.ui.theme.AppUISize.small3XRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.tiny6X
import kotlinx.coroutines.runBlocking
import org.koin.compose.koinInject

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PasteDataScope.SidePasteItemView(
    selected: Boolean,
    isScrolling: Boolean,
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
    val placeholderColor = AppUIColors.pasteBackground

    Row(
        modifier =
            Modifier
                .dragAndDropSource(
                    drawDragDecoration = {
                        // The graphics layer is empty until the first non-scrolling
                        // frame records it. If a drag starts before that, fall back
                        // to a blank tile sized to the source so the preview is
                        // still visible — the drag itself shouldn't be blocked by
                        // rendering state.
                        if (graphicsLayer.size == IntSize.Zero) {
                            drawRect(color = placeholderColor)
                        } else {
                            drawLayer(graphicsLayer)
                        }
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
                        // Always render the visible content directly. The graphics
                        // layer is only used to provide a drag preview, so we keep
                        // recording off the per-frame draw path of the visible UI.
                        drawContent()
                        // Skip layer recording while the LazyRow is actively
                        // scrolling. During fast recycling a freshly composed text
                        // child can still have a null MultiParagraphLayoutCache,
                        // and re-driving the child draw via record { drawContent() }
                        // would crash. The previously recorded layer remains
                        // available for any drag started mid-scroll.
                        if (!isScrolling) {
                            try {
                                graphicsLayer.record {
                                    this@drawWithContent.drawContent()
                                }
                            } catch (_: IllegalStateException) {
                                // Residual race on the first stable frame: keep
                                // the prior recording and try again next frame.
                            }
                        }
                    },
        ) {
            Card(
                modifier =
                    Modifier.fillMaxSize(),
                shape = small3XRoundedCornerShape,
                elevation = highlightedCardElevation,
                colors =
                    CardDefaults.cardColors(
                        containerColor = AppUIColors.pasteBackground,
                    ),
                border =
                    BorderStroke(
                        width = tiny6X,
                        color = AppUIColors.lightBorderColor,
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
