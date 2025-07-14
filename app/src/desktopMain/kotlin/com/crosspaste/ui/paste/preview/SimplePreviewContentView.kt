package com.crosspaste.ui.paste.preview

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.crosspaste.db.paste.PasteData
import com.crosspaste.paste.DesktopPasteMenuService
import com.crosspaste.ui.paste.PasteContextMenuView
import org.koin.compose.koinInject

@Composable
fun SimplePreviewContentView(
    pasteData: PasteData,
    content: @Composable () -> Unit,
) {
    val pasteMenuService = koinInject<DesktopPasteMenuService>()

    PasteSpecificPreviewContentView(
        pasteMainContent = {
            Row(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    pasteMenuService.copyPasteData(pasteData)
                                },
                                onDoubleTap = {
                                    pasteMenuService.quickPasteFromMainWindow(pasteData)
                                },
                            )
                        },
            ) {
                PasteContextMenuView(
                    items = pasteMenuService.pasteMenuItemsProvider(pasteData),
                ) {
                    content()
                }
            }
        },
        pasteRightInfo = { toShow ->
            PasteMenuView(pasteData = pasteData, toShow = toShow)
        },
    )
}
