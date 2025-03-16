package com.crosspaste.ui.paste.preview

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crosspaste.db.paste.PasteData
import com.crosspaste.paste.DesktopPasteMenuService
import com.crosspaste.paste.item.PasteText
import org.koin.compose.koinInject

@Composable
fun TextPreviewView(pasteData: PasteData) {
    pasteData.getPasteItem(PasteText::class)?.let { pasteText ->
        val pasteMenuService = koinInject<DesktopPasteMenuService>()

        PasteSpecificPreviewContentView(
            pasteMainContent = {
                Row(
                    modifier =
                        Modifier.fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = {
                                        pasteMenuService.copyPasteData(pasteData)
                                    },
                                    onDoubleTap = {
                                        pasteMenuService.quickPaste(pasteData)
                                    },
                                )
                            }
                            .padding(10.dp),
                ) {
                    PasteContextMenuView(
                        items = pasteMenuService.pasteMenuItemsProvider(pasteData),
                    ) {
                        Text(
                            modifier = Modifier.fillMaxSize(),
                            text = pasteText.previewText(),
                            fontFamily = FontFamily.SansSerif,
                            maxLines = 4,
                            softWrap = true,
                            overflow = TextOverflow.Ellipsis,
                            style =
                                TextStyle(
                                    fontWeight = FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 14.sp,
                                ),
                        )
                    }
                }
            },
            pasteRightInfo = { toShow ->
                PasteMenuView(pasteData = pasteData, toShow = toShow)
            },
        )
    }
}
