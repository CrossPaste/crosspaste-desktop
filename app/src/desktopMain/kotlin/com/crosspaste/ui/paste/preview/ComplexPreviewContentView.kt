package com.crosspaste.ui.paste.preview

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.crosspaste.paste.DesktopPasteMenuService
import com.crosspaste.ui.paste.PasteDataScope
import org.koin.compose.koinInject

@Composable
fun PasteDataScope.ComplexPreviewContentView(content: LazyListScope.() -> Unit) {
    val pasteMenuService = koinInject<DesktopPasteMenuService>()

    PasteSpecificPreviewContentView(
        pasteMainContent = {
            LazyRow(
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
                content()
            }
        },
        pasteRightInfo = { toShow ->
            PasteMenuView(toShow = toShow)
        },
    )
}
